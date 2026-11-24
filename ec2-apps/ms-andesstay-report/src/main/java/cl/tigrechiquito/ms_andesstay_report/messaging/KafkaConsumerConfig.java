package cl.tigrechiquito.ms_andesstay_report.messaging;

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
 * Resiliencia de los consumidores de report:
 *  - ErrorHandlingDeserializer (application.yml): un JSON corrupto no rompe el loop.
 *  - Errores transitorios (ej. la BD no responde): 3 intentos, 1 s entre cada uno.
 *  - Errores permanentes (payload inválido o ilegible): sin reintentos.
 *  - Lo que no se pudo procesar va a "<tópico>.DLT", con un log que dice de dónde salió.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    private static final long FOURTEEN_DAYS_MS = 14L * 24 * 60 * 60 * 1000;

    @Bean
    NewTopic reservationsEventsDlt(@Value("${andesstay.kafka.replicas:1}") int replicas) {
        return dlt(KafkaConstants.TOPIC_RESERVATIONS_EVENTS, replicas);
    }

    @Bean
    NewTopic unitsStateDlt(@Value("${andesstay.kafka.replicas:1}") int replicas) {
        return dlt(KafkaConstants.TOPIC_UNITS_STATE, replicas);
    }

    /** DLT: se guarda 14 días, margen para revisar y reprocesar a mano. */
    private static NewTopic dlt(String sourceTopic, int replicas) {
        return TopicBuilder.name(sourceTopic + KafkaConstants.DLT_SUFFIX)
                .partitions(3)        // misma partición que el original
                .replicas(replicas)   // dev = 1 broker; en ec2-kafka -> 3 (ANDESSTAY_KAFKA_REPLICAS)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(FOURTEEN_DAYS_MS))
                .build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                template,
                (record, ex) -> new TopicPartition(record.topic() + KafkaConstants.DLT_SUFFIX, record.partition()));

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
