package cl.tigrechiquito.ms_andesstay_catalog.dto;

import java.math.BigDecimal;

import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Payload de actualización de una unidad (PUT). Cambiar totalSlots reajusta la
 * disponibilidad de forma coherente (ver Unit.adjustTotalSlots).
 */
public record UpdateUnitRequest(

        @NotBlank(message = "name es obligatorio")
        String name,

        @NotNull(message = "type es obligatorio")
        UnitType type,

        String location,

        @NotNull(message = "totalSlots es obligatorio")
        @Positive(message = "totalSlots debe ser mayor a 0")
        Integer totalSlots,

        @NotNull(message = "pricePerNight es obligatorio")
        @Positive(message = "pricePerNight debe ser mayor a 0")
        BigDecimal pricePerNight,

        @NotNull(message = "active es obligatorio")
        Boolean active
) {
}