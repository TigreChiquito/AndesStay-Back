package cl.tigrechiquito.ms_andesstay_audit.messaging;

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
 * Resiliencia del consumidor: ErrorHandlingDeserializer (en application.yml) para
 * que un mensaje corrupto no rompa el consumo, y este error handler que reintenta
 * y luego manda a la DLT propia de audit.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    NewTopic auditDlt() {
        return TopicBuilder.name(KafkaConstants.TOPIC_DLT)
                .partitions(3)
                .replicas(1)   // dev = 1 broker; en ec2-kafka -> 3
                .build();
    }

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                template,
                (record, exception) -> new TopicPartition(
                        KafkaConstants.TOPIC_DLT, record.partition()));

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}