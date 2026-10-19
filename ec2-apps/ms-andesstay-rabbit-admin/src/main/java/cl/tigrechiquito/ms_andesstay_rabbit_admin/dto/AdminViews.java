package cl.tigrechiquito.ms_andesstay_rabbit_admin.dto;

import java.util.List;

/** Respuestas de lectura del administrador. */
public final class AdminViews {

    private AdminViews() {
    }

    /**
     * Métricas que pide la pauta + estado del clúster.
     *
     * @param activeQueues    colas existentes
     * @param pendingMessages mensajes en todas las colas (listos + sin confirmar)
     * @param consumers       consumidores conectados
     * @param dlqMessages     mensajes acumulados en las DLQ (*.dlq)
     */
    public record Overview(
            int activeQueues,
            long pendingMessages,
            int consumers,
            long dlqMessages,
            List<Node> nodes,
            String managementUrl
    ) {
    }

    public record Node(String name, boolean running) {
    }

    /**
     * Cola con sus métricas y su configuración de DLQ y retención.
     *
     * @param deadLetterQueue nombre de su DLQ (si tiene x-dead-letter-routing-key)
     * @param messageTtlMs    x-message-ttl; null si no tiene
     * @param maxLength       x-max-length; null si no tiene
     */
    public record QueueView(
            String name,
            boolean durable,
            String state,
            String node,
            long messages,
            long messagesReady,
            long messagesUnacknowledged,
            int consumers,
            boolean dlq,
            String deadLetterQueue,
            Long messageTtlMs,
            Long maxLength,
            String overflow,
            boolean protectedResource
    ) {
    }

    public record ExchangeView(String name, String type, boolean durable, boolean protectedResource) {
    }

    public record BindingView(String exchange, String queue, String routingKey, boolean protectedResource) {
    }

    /** Métricas de una cola obtenidas por AMQP (RabbitAdmin.getQueueInfo). */
    public record QueueInfo(String name, long messageCount, int consumerCount) {
    }
}
