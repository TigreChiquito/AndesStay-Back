package cl.tigrechiquito.ms_andesstay_rabbit_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Crear un exchange de tipo direct, topic o fanout. */
public record CreateExchangeRequest(
        @NotBlank(message = "name es obligatorio")
        @Size(max = 255, message = "name admite hasta 255 caracteres")
        @Pattern(regexp = NamePatterns.NAME, message = NamePatterns.NAME_MESSAGE)
        String name,
        @NotBlank(message = "type es obligatorio")
        @Pattern(regexp = "direct|topic|fanout", message = "type debe ser direct, topic o fanout")
        String type,
        Boolean durable
) {

    public boolean isDurable() {
        return durable == null || durable;
    }
}
