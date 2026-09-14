package cl.tigrechiquito.ms_andesstay_reservations.messaging;

/**
 * Nombres de la topología RabbitMQ definidos en la sección 8 de la pauta.
 * Centralizados acá para no repartir strings mágicos por el código.
 */
public final class RabbitConstants {

    private RabbitConstants() {
    }

    // --- Exchanges ---
    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC = "cmd.topic";
    public static final String EXCHANGE_DLX = "cmd.dead.dlx";

    // --- Colas principales ---
    public static final String QUEUE_EMAIL = "q.cmd.email";
    public static final String QUEUE_HOUSEKEEPING = "q.cmd.housekeeping";
    public static final String QUEUE_VOUCHER = "q.cmd.voucher";

    // --- Colas de mensajes muertos (DLQ) ---
    public static final String QUEUE_EMAIL_DLQ = "q.cmd.email.dlq";
    public static final String QUEUE_HOUSEKEEPING_DLQ = "q.cmd.housekeeping.dlq";
    public static final String QUEUE_VOUCHER_DLQ = "q.cmd.voucher.dlq";

    // --- Routing keys del exchange direct (match exacto) ---
    public static final String RK_EMAIL_SEND = "email.send";
    public static final String RK_HOUSEKEEPING_TICKET = "housekeeping.ticket";
    public static final String RK_VOUCHER_GEN = "voucher.gen";

    // --- Patrones de binding del exchange topic ---
    public static final String PATTERN_EMAIL = "email.*";
    public static final String PATTERN_HOUSEKEEPING = "housekeeping.#";
    public static final String PATTERN_VOUCHER = "voucher.*";

    // --- Routing keys hacia las DLQ (dead-letter) ---
    public static final String RK_EMAIL_DLQ = "email.dlq";
    public static final String RK_HOUSEKEEPING_DLQ = "housekeeping.dlq";
    public static final String RK_VOUCHER_DLQ = "voucher.dlq";
}