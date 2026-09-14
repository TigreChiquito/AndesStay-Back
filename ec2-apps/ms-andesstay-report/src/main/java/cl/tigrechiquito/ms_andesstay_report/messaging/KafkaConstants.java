package cl.tigrechiquito.ms_andesstay_report.messaging;

/**
 * Nombres de tópicos Kafka que usa report.
 */
public final class KafkaConstants {

    private KafkaConstants() {
    }

    /** Tópico origen: eventos de la reserva (lo produce reservations). */
    public static final String TOPIC_RESERVATIONS_EVENTS = "reservations.events";

    /** DLT propia de report: mensajes que fallaron tras los reintentos. */
    public static final String TOPIC_RESERVATIONS_EVENTS_DLT = "reservations.events.DLT";
}