package gt.edu.umg.desarrolloweb.pagos.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the main queue and its Dead Letter Queue.
 *
 * A message is dead-lettered when the consumer rejects it (AmqpRejectAndDontRequeueException)
 * or when the configured retries are exhausted. RabbitMQ keeps the original routing key,
 * so the DLQ exchange is bound with the same key.
 */
@Configuration
public class RabbitConfig {

    @Value("${app.rabbit.exchange}")
    private String exchange;

    @Value("${app.rabbit.queue}")
    private String queue;

    @Value("${app.rabbit.routing-key}")
    private String routingKey;

    @Value("${app.rabbit.dlq-exchange}")
    private String dlqExchange;

    @Value("${app.rabbit.dlq-queue}")
    private String dlqQueue;

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(exchange);
    }

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", dlqExchange)
                .withArgument("x-dead-letter-routing-key", routingKey)
                .build();
    }

    @Bean
    public Binding binding() {
        return BindingBuilder.bind(queue()).to(exchange()).with(routingKey);
    }

    @Bean
    public DirectExchange dlqExchange() {
        return new DirectExchange(dlqExchange);
    }

    @Bean
    public Queue dlqQueue() {
        return QueueBuilder.durable(dlqQueue).build();
    }

    @Bean
    public Binding dlqBinding() {
        return BindingBuilder.bind(dlqQueue()).to(dlqExchange()).with(routingKey);
    }
}
