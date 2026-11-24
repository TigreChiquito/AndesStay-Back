package cl.tigrechiquito.ms_andesstay_catalog.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara units.state: el inventario actual de unidades.
 *
 * cleanup.policy=compact: Kafka conserva solo el último mensaje de cada unitId
 * (como una tabla), así que no hace falta retención por tiempo. segment.ms corto
 * para que los segmentos se cierren y la compactación pueda actuar.
 */
@Configuration
public class KafkaTopicConfig {

    public static final String TOPIC_UNITS_STATE = "units.state";

    private static final long ONE_HOUR_MS = 60L * 60 * 1000;

    @Bean
    NewTopic unitsStateTopic(@Value("${andesstay.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(TOPIC_UNITS_STATE)
                .partitions(3)
                .replicas(replicas)   // dev = 1 broker; en ec2-kafka -> 3 (ANDESSTAY_KAFKA_REPLICAS)
                .compact()
                .config(TopicConfig.SEGMENT_MS_CONFIG, String.valueOf(ONE_HOUR_MS))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, String.valueOf(Math.min(2, replicas)))
                .build();
    }
}
