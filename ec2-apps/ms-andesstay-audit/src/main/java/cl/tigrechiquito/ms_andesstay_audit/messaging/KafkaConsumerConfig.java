package cl.tigrechiquito.ms_andesstay_audit.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Resiliencia del consumidor de audit:
 *  - ErrorHandlingDeserializer (application.yml): un JSON corrupto no rompe el loop.
 *  - Errores transitorios: 3 intentos, 1 s entre cada uno.
 *  - Errores permanentes (payload inválido o ilegible): sin reintentos.
 *  - Lo que no se pudo procesar va a la DLT propia de audit, con un log que dice de dónde salió.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final long FOURTEEN_DAYS_MS = 14L * 24 * 60 * 60 * 1000;

    /** DLT: se guarda 14 días, margen para revisar y reprocesar a mano. */
    @Bean
    NewTopic auditDlt(@Value("${andesstay.kafka.replicas:1}") int replicas) {
        return TopicBuilder.name(KafkaConstants.TOPIC_DLT)
                .partitions(3)
                .replicas(replicas)   // dev = 1 broker; en ec2-kafka -> 3 (ANDESSTAY_KAFKA_REPLICAS)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(FOURTEEN_DAYS_MS))
                .build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(KafkaConstants.TOPIC_DLT, record.partition()));

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Mensaje a DLT: topic={} partition={} offset={} key={} causa={}",
                    record.topic(), record.partition(), record.offset(), record.key(),
                    NestedExceptionUtils.getMostSpecificCause(ex).getMessage());
            dlt.accept(record, ex);
        }, new FixedBackOff(1000L, 2L));
        // Reintentar no arregla un payload inválido (los de deserialización ya vienen excluidos)
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
