package cl.tigrechiquito.ms_andesstay_kafka_admin.dto;

import java.time.Instant;
import java.util.Map;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Cuerpos de las peticiones del administrador. */
public final class KafkaRequests {

    private KafkaRequests() {
    }

    /** Mismas reglas de nombre que Kafka: letras, números, '.', '_' y '-'. */
    public record CreateTopic(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{1,200}",
                    message = "solo letras, números, '.', '_' y '-' (máx. 200)") String name,
            @NotNull @Min(1) @Max(50) Integer partitions,
            @NotNull @Min(1) @Max(3) Integer replicationFactor,   // 3 brokers en ec2-kafka
            Map<String, String> configs) {
    }

    /** Valor vacío o null = quitar el override y volver al default del broker. */
    public record UpdateConfigs(@NotEmpty Map<String, String> configs) {
    }

    /** Kafka solo permite AUMENTAR particiones. */
    public record AddPartitions(@NotNull @Min(1) @Max(50) Integer totalPartitions) {
    }

    public enum ResetTo { EARLIEST, LATEST, DATETIME }

    /** datetime solo se usa con DATETIME (ISO-8601, ej. 2026-11-21T12:00:00Z). */
    public record ResetOffsets(@NotBlank String topic, @NotNull ResetTo to, Instant datetime) {
    }
}
