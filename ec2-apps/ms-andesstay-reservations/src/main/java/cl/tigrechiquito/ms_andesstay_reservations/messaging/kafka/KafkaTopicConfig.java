package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara el tópico reservations.events. Spring lo crea en el broker al arrancar
 * (vía KafkaAdmin) si no existe.
 *
 * OJO con las réplicas: en desarrollo hay 1 solo broker, así que la réplica es 1.
 * En ec2-kafka (3 brokers) la pauta pide 3 réplicas y 3 particiones.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic reservationsEventsTopic() {
        return TopicBuilder.name(KafkaConstants.TOPIC_RESERVATIONS_EVENTS)
                .partitions(3)
                .replicas(1)   // dev = 1 broker; en ec2-kafka -> 3
                .build();
    }
}