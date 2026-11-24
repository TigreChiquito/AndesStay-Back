package cl.tigrechiquito.ms_andesstay_kafka_admin.domain;

/** Se mapea a HTTP 503: Kafka no responde. */
public class KafkaUnavailableException extends RuntimeException {

    public KafkaUnavailableException(String message) {
        super(message);
    }
}
