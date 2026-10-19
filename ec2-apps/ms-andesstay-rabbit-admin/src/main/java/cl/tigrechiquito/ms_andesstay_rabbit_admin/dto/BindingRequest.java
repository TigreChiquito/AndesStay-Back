package cl.tigrechiquito.ms_andesstay_rabbit_admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Binding exchange -> cola. La routing key puede ser vacía (fanout la ignora) y,
 * en un exchange topic, admite comodines: * (una palabra) y # (cero o más).
 */
public record BindingRequest(
        @NotBlank(message = "exchange es obligatorio")
        @Pattern(regexp = NamePatterns.NAME, message = "exchange: " + NamePatterns.NAME_MESSAGE)
        String exchange,
        @NotBlank(message = "queue es obligatorio")
        @Pattern(regexp = NamePatterns.NAME, message = "queue: " + NamePatterns.NAME_MESSAGE)
        String queue,
        @NotNull(message = "routingKey es obligatorio (puede ser vacío)")
        @Size(max = 255, message = "routingKey admite hasta 255 caracteres")
        @Pattern(regexp = "[A-Za-z0-9._*#-]*",
                message = "routingKey solo admite letras, números, '.', '_', '-', '*' y '#'")
        String routingKey
) {
}
