package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

/**
 * Nombres de la topología Kafka (sección 9 de la pauta).
 */
public final class KafkaConstants {

    private KafkaConstants() {
    }

    /** Fuente de verdad de los eventos de la reserva. Alimenta report y audit. */
    public static final String TOPIC_RESERVATIONS_EVENTS = "reservations.events";
}