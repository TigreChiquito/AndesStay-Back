package cl.tigrechiquito.ms_andesstay_reservations.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

/**
 * Payload de creación de una reserva.
 *
 * {@code guestId} solo lo respeta el personal (Recepcionista/Admin), que lo
 * necesita para registrar a un huésped; para el resto se toma de X-User-Id.
 */
public record CreateReservationRequest(

        String guestId,

        String guestName,

        @NotNull(message = "unitId es obligatorio")
        Long unitId,

        @NotNull(message = "checkInDate es obligatorio")
        LocalDate checkInDate,

        @NotNull(message = "checkOutDate es obligatorio")
        LocalDate checkOutDate
) {
}