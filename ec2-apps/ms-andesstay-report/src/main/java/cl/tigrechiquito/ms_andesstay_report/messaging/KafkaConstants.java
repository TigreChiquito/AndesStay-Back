package cl.tigrechiquito.ms_andesstay_report.messaging;

/**
 * Tópicos y consumer groups que usa report.
 */
public final class KafkaConstants {

    private KafkaConstants() {
    }

    /** Eventos de la reserva (lo produce reservations). */
    public static final String TOPIC_RESERVATIONS_EVENTS = "reservations.events";

    /** Estado actual de cada unidad, compactado (lo produce catalog). */
    public static final String TOPIC_UNITS_STATE = "units.state";

    /** Sufijo de las DLT de report: "<tópico>.DLT". */
    public static final String DLT_SUFFIX = ".DLT";

    /**
     * Grupo propio para el inventario: lleva sus offsets aparte del de reservas, así
     * se puede reconstruir (reset al inicio) sin reprocesar las reservas.
     */
    public static final String GROUP_INVENTORY = "ms-andesstay-report-inventory";
}
