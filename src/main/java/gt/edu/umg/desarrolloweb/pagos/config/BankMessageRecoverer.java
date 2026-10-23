package gt.edu.umg.desarrolloweb.pagos.config;

import gt.edu.umg.desarrolloweb.pagos.entity.Pago;
import gt.edu.umg.desarrolloweb.pagos.repository.PagoRepository;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

/**
 * Runs when the retries of a transient error are exhausted.
 *
 * It marks the payment as "Fallido - Revisar" and then rejects the message so RabbitMQ
 * routes it to the configured Dead Letter Exchange (pagos.dlx -> pagos.dlq).
 */
public class BankMessageRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(BankMessageRecoverer.class);

    private final PagoRepository repo;

    public BankMessageRecoverer(PagoRepository repo) {
        this.repo = repo;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        Integer pagoId = readPagoId(message);
        if (pagoId != null) {
            repo.findById(pagoId).ifPresent(pago -> markAsFailed(pago));
        }
        log.error("[CONSUMIDOR] Reintentos agotados para el pago {}. Enviando a DLQ.", pagoId, cause);
        throw new ListenerExecutionFailedException(
                "Retry Policy Exhausted",
                new AmqpRejectAndDontRequeueException(cause),
                message);
    }

    private void markAsFailed(Pago pago) {
        pago.setEstado("Fallido - Revisar");
        repo.save(pago);
    }

    private Integer readPagoId(Message message) {
        byte[] body = message.getBody();
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(body))) {
            Object deserialized = in.readObject();
            if (deserialized instanceof Integer id) {
                return id;
            }
            if (deserialized != null) {
                return Integer.valueOf(deserialized.toString());
            }
        } catch (Exception ex) {
            // The body may not be Java-serialized; fall back to plain text.
        }
        try {
            return Integer.valueOf(new String(body, StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException ex) {
            log.warn("[CONSUMIDOR] No se pudo leer el id del pago desde la DLQ: {}", ex.getMessage());
            return null;
        }
    }
}
