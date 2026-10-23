package gt.edu.umg.desarrolloweb.pagos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import gt.edu.umg.desarrolloweb.pagos.bank.client.BankApiClient;
import gt.edu.umg.desarrolloweb.pagos.bank.dto.BankErrorDto;
import gt.edu.umg.desarrolloweb.pagos.bank.dto.BankPaymentReceiptDto;
import gt.edu.umg.desarrolloweb.pagos.bank.dto.BankPaymentRequestDto;
import gt.edu.umg.desarrolloweb.pagos.bank.dto.MoneyDto;
import gt.edu.umg.desarrolloweb.pagos.bank.dto.PayerDto;
import gt.edu.umg.desarrolloweb.pagos.bank.dto.TeamCodeDto;
import gt.edu.umg.desarrolloweb.pagos.entity.Pago;
import gt.edu.umg.desarrolloweb.pagos.repository.PagoRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Consumer: takes a payment id from RabbitMQ, maps the monolith row to the bank contract
 * and sends it with Feign.
 *
 * Response rules (decided by HTTP status, not by the message text):
 *  - 201 / 409 DUPLICATE_PAYMENT -> ACK, estado Completado, guardar receiptId
 *  - 400 / 403 / 422 / 409 IDEMPOTENCY_KEY_REUSED -> DLQ immediately
 *  - 429 / 5xx / timeout -> retry with Spring Retry backoff, then DLQ
 */
@Service
public class PagoConsumidor {

    private static final Logger log = LoggerFactory.getLogger(PagoConsumidor.class);

    private static final String ESTADO_COMPLETADO = "Completado";
    private static final String ESTADO_FALLIDO = "Fallido - Revisar";
    private static final long MONTO_MAXIMO_CENTAVOS = 2_500_000L; // Q25,000.00

    private final PagoRepository repo;
    private final BankApiClient bankClient;
    private final ObjectMapper objectMapper;

    @Value("${bank.team-code}")
    private String teamCode;

    @Value("${bank.simulation-scenario:}")
    private String simulationScenario;

    public PagoConsumidor(PagoRepository repo, BankApiClient bankClient, ObjectMapper objectMapper) {
        this.repo = repo;
        this.bankClient = bankClient;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = "${app.rabbit.queue}")
    public void procesarPago(Integer pagoId) {
        Pago pago = repo.findById(pagoId)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Pago no existe: " + pagoId));

        if (ESTADO_COMPLETADO.equals(pago.getEstado())) {
            log.info("[CONSUMIDOR] Pago {} ya estaba completado; se ignora el mensaje.", pagoId);
            return;
        }

        try {
            BankPaymentRequestDto request = buildRequest(pago);
            String idempotencyKey = buildIdempotencyKey(pago);

            // x-api-key is added by the generated interceptor; correlation id by FeignConfig.
            ResponseEntity<BankPaymentReceiptDto> response = bankClient.sendBankPayment(
                    idempotencyKey, request, null, scenarioHeader());

            BankPaymentReceiptDto receipt = response.getBody();
            String receiptId = receipt == null ? null : receipt.getReceiptId();
            markCompleted(pago, receiptId);
            log.info("[CONSUMIDOR] Pago {} registrado en el Banco. Estado={} receiptId={}",
                    pago.getId(), response.getStatusCode().value(), receiptId);
        } catch (FeignException ex) {
            handleBankError(pago, ex);
        } catch (AmqpRejectAndDontRequeueException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            // Configuration or mapping error (for example an unknown teamCode): no retry.
            markAsFailed(pago);
            throw new AmqpRejectAndDontRequeueException("Datos del pago invalidos: " + ex.getMessage(), ex);
        }
    }

    private BankPaymentRequestDto buildRequest(Pago pago) {
        BankPaymentRequestDto request = new BankPaymentRequestDto();
        request.setTeamCode(TeamCodeDto.fromValue(teamCode));

        PayerDto payer = new PayerDto();
        payer.setCustomerCode("CLI-" + pago.getId());
        request.setPayer(payer);

        MoneyDto money = new MoneyDto();
        money.setValueInCents(toCents(pago));
        money.setCurrency(MoneyDto.CurrencyEnum.GTQ);
        request.setAmount(money);

        request.setTransactionDate(transactionDate(pago));
        request.setChannel(mapChannel(pago.getMetodo()));
        request.setDescription(truncate("Servicio de agua pago " + pago.getId(), 140));
        request.putMetadataItem("sourceSystem", "springboot-oficina-agua");
        return request;
    }

    /**
     * BigDecimal -> integer cents without ever going through double.
     * Q150.75 -> 15075. The bank rejects amounts over Q25,000.00, so we fail
     * before spending a network call.
     */
    private long toCents(Pago pago) {
        BigDecimal monto = pago.getMonto();
        if (monto == null) {
            markAsFailed(pago);
            throw new AmqpRejectAndDontRequeueException("El pago " + pago.getId() + " no tiene monto");
        }
        long cents;
        try {
            cents = monto.setScale(2, RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            markAsFailed(pago);
            throw new AmqpRejectAndDontRequeueException("Monto invalido en el pago " + pago.getId(), ex);
        }
        if (cents < 1 || cents > MONTO_MAXIMO_CENTAVOS) {
            markAsFailed(pago);
            throw new AmqpRejectAndDontRequeueException(
                    "Monto fuera del rango permitido en el pago " + pago.getId() + ": " + cents);
        }
        return cents;
    }

    private OffsetDateTime transactionDate(Pago pago) {
        LocalDate fecha = pago.getFechaPago();
        if (fecha == null) {
            return OffsetDateTime.now(ZoneOffset.UTC);
        }
        // The monolith stores DATETIME without zone: interpret it in Guatemala (UTC-6).
        return fecha.atStartOfDay(ZoneId.of("America/Guatemala")).toOffsetDateTime();
    }

    private BankPaymentRequestDto.ChannelEnum mapChannel(String metodo) {
        String value = metodo == null ? "" : metodo.toLowerCase(Locale.ROOT);
        if (value.contains("tarjeta")) {
            return BankPaymentRequestDto.ChannelEnum.CARD;
        }
        if (value.contains("transferencia")) {
            return BankPaymentRequestDto.ChannelEnum.BANK_TRANSFER;
        }
        if (value.contains("cheque")) {
            return BankPaymentRequestDto.ChannelEnum.CHECK;
        }
        return BankPaymentRequestDto.ChannelEnum.CASH;
    }

    /**
     * Stable key, derived from the monolith payment id: every retry of the same payment
     * uses the same value. Never generate a random UUID per attempt.
     */
    private String buildIdempotencyKey(Pago pago) {
        String prefix = teamCode != null && teamCode.startsWith("EQUIPO_")
                ? "EQ" + teamCode.substring("EQUIPO_".length())
                : "EQX";
        return prefix + "-" + String.format("%06d", pago.getId());
    }

    private String scenarioHeader() {
        return simulationScenario == null || simulationScenario.isBlank()
                ? null
                : simulationScenario.trim();
    }

    private void handleBankError(Pago pago, FeignException ex) {
        int status = ex.status();
        BankErrorDto error = parseError(ex);
        String code = error == null ? null : error.getCode();

        if (status == 409) {
            if ("IDEMPOTENCY_KEY_REUSED".equals(code)) {
                markAsFailed(pago);
                log.error("[CONSUMIDOR] Pago {} reutilizo la Idempotency-Key con otros datos (409 {}).",
                        pago.getId(), code);
                throw new AmqpRejectAndDontRequeueException("Idempotency-Key reutilizada", ex);
            }
            // Same payment already registered: that is a success for us.
            String receiptId = error != null && error.getOriginalReceipt() != null
                    ? error.getOriginalReceipt().getReceiptId()
                    : null;
            markCompleted(pago, receiptId);
            log.info("[CONSUMIDOR] Pago {} ya estaba en el Banco (409 {}). Completado. receiptId={}",
                    pago.getId(), code, receiptId);
            return;
        }

        if (status == 429 || status >= 500 || status <= 0) {
            log.warn("[CONSUMIDOR] Error transitorio en pago {} (http={} code={} retryAfter={}). Reintentando...",
                    pago.getId(), status, code, retryAfter(ex));
            throw ex; // Spring Retry applies the backoff; after 3 attempts -> DLQ.
        }

        // 400, 403, 404, 422 and any other non-retryable 4xx.
        markAsFailed(pago);
        log.error("[CONSUMIDOR] Pago {} rechazado por el Banco (http={} code={}). Enviado a DLQ.",
                pago.getId(), status, code);
        throw new AmqpRejectAndDontRequeueException("Rechazo del banco: " + status + " " + code, ex);
    }

    private BankErrorDto parseError(FeignException ex) {
        try {
            String body = ex.contentUTF8();
            if (body == null || body.isBlank()) {
                return null;
            }
            return objectMapper.readValue(body, BankErrorDto.class);
        } catch (Exception ignored) {
            // API Gateway errors such as {"message":"Forbidden"} do not follow BankError.
            return null;
        }
    }

    private String retryAfter(FeignException ex) {
        Map<String, java.util.Collection<String>> headers = ex.responseHeaders();
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, java.util.Collection<String>> entry : headers.entrySet()) {
            if ("retry-after".equalsIgnoreCase(entry.getKey())) {
                return String.join(",", entry.getValue());
            }
        }
        return null;
    }

    private void markCompleted(Pago pago, String receiptId) {
        pago.setEstado(ESTADO_COMPLETADO);
        if (receiptId != null && !receiptId.isBlank()) {
            pago.setComprobante(receiptId);
        }
        repo.save(pago);
    }

    private void markAsFailed(Pago pago) {
        pago.setEstado(ESTADO_FALLIDO);
        repo.save(pago);
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
