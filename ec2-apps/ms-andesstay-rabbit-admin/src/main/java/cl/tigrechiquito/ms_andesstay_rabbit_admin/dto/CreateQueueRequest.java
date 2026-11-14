package cl.tigrechiquito.ms_andesstay_rabbit_admin.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Crear una cola. Opcionalmente con DLQ propia y política de retención.
 *
 * @param durable      sobrevive a un reinicio del broker (por defecto true)
 * @param withDlq      crea además "&lt;name&gt;.dlq" y la conecta vía cmd.dead.dlx
 * @param messageTtlMs los mensajes expiran tras este tiempo (x-message-ttl)
 * @param maxLength    largo máximo de la cola (x-max-length)
 * @param overflow     qué hacer al llenarse: drop-head (default de RabbitMQ),
 *                     reject-publish o reject-publish-dlx (x-overflow)
 */
public record CreateQueueRequest(
        @NotBlank(message = "name es obligatorio")
        @Size(max = 250, message = "name admite hasta 250 caracteres")
        @Pattern(regexp = NamePatterns.NAME, message = NamePatterns.NAME_MESSAGE)
        String name,
        Boolean durable,
        Boolean withDlq,
        @Positive(message = "messageTtlMs debe ser mayor a 0")
        Long messageTtlMs,
        @Positive(message = "maxLength debe ser mayor a 0")
        Integer maxLength,
        @Pattern(regexp = "drop-head|reject-publish|reject-publish-dlx",
                message = "overflow debe ser drop-head, reject-publish o reject-publish-dlx")
        String overflow
) {

    public boolean isDurable() {
        return durable == null || durable;
    }

    // Boolean (no boolean): con Jackson 3 un primitivo ausente en el JSON es un error
    public boolean isWithDlq() {
        return Boolean.TRUE.equals(withDlq);
    }

    /** Sin DLQ, reject-publish-dlx no tiene a dónde mandar los rechazados y se pierden. */
    @AssertTrue(message = "reject-publish-dlx necesita la DLQ: marca withDlq o elige otro overflow")
    public boolean isOverflowDlxWithDlq() {
        return !"reject-publish-dlx".equals(overflow) || isWithDlq();
    }

    /** overflow solo actúa cuando la cola se llena, y eso requiere un largo máximo. */
    @AssertTrue(message = "overflow necesita maxLength: sin largo máximo la cola nunca se llena")
    public boolean isOverflowWithMaxLength() {
        return overflow == null || maxLength != null;
    }
}
