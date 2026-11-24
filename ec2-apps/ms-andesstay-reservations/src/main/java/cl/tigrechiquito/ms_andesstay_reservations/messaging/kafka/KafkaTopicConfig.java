package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara el tópico reservations.events. KafkaAdmin lo crea al arrancar si no
 * existe y, con spring.kafka.admin.modify-topic-configs, ajusta su config si cambió.
 *
 * Es un historial de hechos (cleanup "delete"): se guarda 7 días para que report y
 * audit puedan releerlo (replay) y se acota por tamaño para no llenar el disco.
 * Réplicas: 1 en desarrollo (1 broker); 3 en ec2-kafka (ANDESSTAY_KAFKA_REPLICAS).
 */
@Configuration
public class KafkaTopicConfig {

    private static final long SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000;
    private static final long MAX_BYTES_PER_PARTITION = 256L * 1024 * 1024;

    @Bean
    NewTopic reservationsEventsTopic(@Value("${andesstay.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(KafkaConstants.TOPIC_RESERVATIONS_EVENTS)
                .partitions(3)       // = consumidores en paralelo por grupo (concurrency 3)
                .replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(SEVEN_DAYS_MS))
                .config(TopicConfig.RETENTION_BYTES_CONFIG, String.valueOf(MAX_BYTES_PER_PARTITION))
                // con acks=all, tolera la caída de 1 broker; en dev (1 réplica) no puede pasar de 1
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(Math.min(2, replicas)))
                .build();
    }
}
