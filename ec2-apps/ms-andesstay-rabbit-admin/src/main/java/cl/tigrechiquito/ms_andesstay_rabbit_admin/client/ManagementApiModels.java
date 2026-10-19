package cl.tigrechiquito.ms_andesstay_rabbit_admin.client;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * JSON de la API HTTP de la Management UI (solo los campos que usa el administrador).
 * Tipos objeto, no primitivos: RabbitMQ omite las estadísticas de una cola recién
 * creada, y con Jackson 3 un primitivo ausente hace fallar la deserialización.
 */
public final class ManagementApiModels {

    private ManagementApiModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QueueJson(
            String name,
            String type,
            Boolean durable,
            String state,
            String node,
            Long messages,
            @JsonProperty("messages_ready") Long messagesReady,
            @JsonProperty("messages_unacknowledged") Long messagesUnacknowledged,
            Integer consumers,
            Map<String, Object> arguments
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ExchangeJson(String name, String type, Boolean durable, Boolean internal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BindingJson(
            String source,
            String destination,
            @JsonProperty("destination_type") String destinationType,
            @JsonProperty("routing_key") String routingKey
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NodeJson(String name, Boolean running) {
    }
}
