package cl.tigrechiquito.ms_andesstay_reservations.messaging;

import static cl.tigrechiquito.ms_andesstay_reservations.messaging.RabbitConstants.*;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declara la topología RabbitMQ de la pauta (sección 8):
 *  - 3 exchanges: cmd.direct, cmd.topic, cmd.dead.dlx
 *  - 3 colas principales, cada una con dead-letter hacia cmd.dead.dlx
 *  - 3 DLQ ligadas a cmd.dead.dlx
 *  - cada cola principal ligada al direct (key exacta) y al topic (patrón)
 *
 * Spring detecta estos @Bean (Queue/Exchange/Binding) y los declara solo en el
 * broker al abrir conexión. Declarar lo mismo dos veces es idempotente, así que
 * notify puede re-declarar sin problema mientras use estos mismos nombres.
 *
 * Nota: idealmente el "microservicio administrador de RabbitMQ" es el dueño
 * canónico de esta topología; por ahora la declara reservations para poder
 * probar el productor de una.
 */
@Configuration
public class RabbitTopologyConfig {

    // ---------- Exchanges ----------

    @Bean
    DirectExchange cmdDirect() {
        return ExchangeBuilder.directExchange(EXCHANGE_DIRECT).durable(true).build();
    }

    @Bean
    TopicExchange cmdTopic() {
        return ExchangeBuilder.topicExchange(EXCHANGE_TOPIC).durable(true).build();
    }

    @Bean
    DirectExchange cmdDlx() {
        return ExchangeBuilder.directExchange(EXCHANGE_DLX).durable(true).build();
    }

    // ---------- Colas principales (con dead-lettering) ----------

    @Bean
    Queue emailQueue() {
        return QueueBuilder.durable(QUEUE_EMAIL)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_EMAIL_DLQ)
                .build();
    }

    @Bean
    Queue housekeepingQueue() {
        return QueueBuilder.durable(QUEUE_HOUSEKEEPING)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_HOUSEKEEPING_DLQ)
                .build();
    }

    @Bean
    Queue voucherQueue() {
        return QueueBuilder.durable(QUEUE_VOUCHER)
                .deadLetterExchange(EXCHANGE_DLX)
                .deadLetterRoutingKey(RK_VOUCHER_DLQ)
                .build();
    }

    // ---------- DLQ ----------

    @Bean
    Queue emailDlq() {
        return QueueBuilder.durable(QUEUE_EMAIL_DLQ).build();
    }

    @Bean
    Queue housekeepingDlq() {
        return QueueBuilder.durable(QUEUE_HOUSEKEEPING_DLQ).build();
    }

    @Bean
    Queue voucherDlq() {
        return QueueBuilder.durable(QUEUE_VOUCHER_DLQ).build();
    }

    // ---------- Bindings de colas principales: direct + topic ----------

    @Bean
    Binding emailDirectBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdDirect()).with(RK_EMAIL_SEND);
    }

    @Bean
    Binding emailTopicBinding() {
        return BindingBuilder.bind(emailQueue()).to(cmdTopic()).with(PATTERN_EMAIL);
    }

    @Bean
    Binding housekeepingDirectBinding() {
        return BindingBuilder.bind(housekeepingQueue()).to(cmdDirect()).with(RK_HOUSEKEEPING_TICKET);
    }

    @Bean
    Binding housekeepingTopicBinding() {
        return BindingBuilder.bind(housekeepingQueue()).to(cmdTopic()).with(PATTERN_HOUSEKEEPING);
    }

    @Bean
    Binding voucherDirectBinding() {
        return BindingBuilder.bind(voucherQueue()).to(cmdDirect()).with(RK_VOUCHER_GEN);
    }

    @Bean
    Binding voucherTopicBinding() {
        return BindingBuilder.bind(voucherQueue()).to(cmdTopic()).with(PATTERN_VOUCHER);
    }

    // ---------- Bindings de las DLQ hacia cmd.dead.dlx ----------

    @Bean
    Binding emailDlqBinding() {
        return BindingBuilder.bind(emailDlq()).to(cmdDlx()).with(RK_EMAIL_DLQ);
    }

    @Bean
    Binding housekeepingDlqBinding() {
        return BindingBuilder.bind(housekeepingDlq()).to(cmdDlx()).with(RK_HOUSEKEEPING_DLQ);
    }

    @Bean
    Binding voucherDlqBinding() {
        return BindingBuilder.bind(voucherDlq()).to(cmdDlx()).with(RK_VOUCHER_DLQ);
    }

    // ---------- Conversor JSON ----------
    // Boot 4 / Spring AMQP 4 usan Jackson 3: JacksonJsonMessageConverter
    // (el antiguo Jackson2JsonMessageConverter quedó deprecado para remoción).
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}