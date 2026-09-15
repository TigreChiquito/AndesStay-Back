package cl.tigrechiquito.ms_andesstay_audit.messaging;

/**
 * Nombres de tópicos Kafka que usa audit.
 */
public final class KafkaConstants {

    private KafkaConstants() {
    }

    /** Tópico origen: eventos de la reserva (lo produce reservations). */
    public static final String TOPIC_RESERVATIONS_EVENTS = "reservations.events";

    /**
     * DLT propia de audit. La pauta pide una DLT por consumidor, por eso lleva
     * "audit" en el nombre (distinta de la de report).
     */
    public static final String TOPIC_DLT = "reservations.events.audit.DLT";
}