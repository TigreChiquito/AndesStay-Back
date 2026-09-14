package cl.tigrechiquito.ms_andesstay_report.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configura la resiliencia del consumidor:
 *  - El value-deserializer es ErrorHandlingDeserializer (en application.yml), que
 *    envuelve al JacksonJsonDeserializer para que un mensaje corrupto no rompa el
 *    loop de consumo.
 *  - Este error handler reintenta unas veces y, si sigue fallando, publica el
 *    mensaje en la DLT (reservations.events.DLT), como pide la pauta.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    NewTopic reservationsEventsDlt() {
        return TopicBuilder.name(KafkaConstants.TOPIC_RESERVATIONS_EVENTS_DLT)
                .partitions(3)
                .replicas(1)   // dev = 1 broker; en ec2-kafka -> 3
                .build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        // Manda el mensaje fallido a "<tópico>.DLT" manteniendo la misma partición.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT", record.partition()));

        // 1s entre intentos, 2 reintentos (3 intentos en total) antes de la DLT.
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}