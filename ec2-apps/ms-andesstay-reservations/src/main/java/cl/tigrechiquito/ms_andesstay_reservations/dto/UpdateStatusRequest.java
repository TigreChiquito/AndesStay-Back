package cl.tigrechiquito.ms_andesstay_reservations.dto;

import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Body de PUT /api/reservations/{id}/status
 *
 * Ej: { "status": "CONFIRMADA" }
 *
 * La conversión del string al enum (tolerante a tildes/mayúsculas) la hace el
 * {@code @JsonCreator} de {@link ReservationStatus}. Si el valor no es válido,
 * Jackson lo rechaza y termina como 400.
 */
public record UpdateStatusRequest(

        @NotNull(message = "status es obligatorio")
        ReservationStatus status
) {
}
