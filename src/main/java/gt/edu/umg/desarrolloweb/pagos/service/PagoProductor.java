package gt.edu.umg.desarrolloweb.pagos.service;

import gt.edu.umg.desarrolloweb.pagos.entity.Pago;
import gt.edu.umg.desarrolloweb.pagos.repository.PagoRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Producer: every 10 seconds it looks for payments in estado "Pendiente" and sends
 * only their id to RabbitMQ. The HTTP call belongs to the consumer, never to this class.
 */
@Service
public class PagoProductor {

    private static final Logger log = LoggerFactory.getLogger(PagoProductor.class);

    private final PagoRepository repo;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbit.exchange}")
    private String exchange;

    @Value("${app.rabbit.routing-key}")
    private String routingKey;

    public PagoProductor(PagoRepository repo, RabbitTemplate rabbitTemplate) {
        this.repo = repo;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 10000)
    public void enviarPagosACola() {
        List<Pago> pagos = repo.findByEstado("Pendiente");
        for (Pago pago : pagos) {
            rabbitTemplate.convertAndSend(exchange, routingKey, pago.getId());
            pago.setEstado("En cola");
            repo.save(pago);
            log.info("[PRODUCTOR] Pago {} enviado a la cola.", pago.getId());
        }
    }
}
