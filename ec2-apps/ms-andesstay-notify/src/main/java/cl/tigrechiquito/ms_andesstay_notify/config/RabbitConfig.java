package cl.tigrechiquito.ms_andesstay_notify.config;

import static cl.tigrechiquito.ms_andesstay_notify.config.RabbitConstants.*;

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
 * notify re-declara la misma topología que reservations. Es idempotente (mismos
 * nombres y argumentos), y así el consumidor arranca funcional aunque se levante
 * antes que el productor.
 *
 * El conversor JSON por defecto de Spring AMQP 4 infiere el tipo desde la firma
 * del @RabbitListener y confía en todos los paquetes, así que la CommandEnvelope
 * de notify se deserializa aunque venga de otro paquete en reservations.
 */
@Configuration
public class RabbitConfig {

    // Exchanges
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

    // Colas principales (con dead-lettering)
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

    // DLQ
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

    // Bindings colas principales (direct + topic)
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

    // Bindings DLQ -> cmd.dead.dlx
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

    // Conversor JSON (Jackson 3). Por defecto infiere el tipo del listener.
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}