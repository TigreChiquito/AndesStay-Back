package cl.tigrechiquito.ms_andesstay_reservations.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de creación de una reserva.
 *
 * Nota: por ahora {@code guestId} llega en el body. Cuando conectemos la
 * seguridad, este dato saldrá del token de Azure AD (sub/oid) inyectado por el
 * BFF, y se podrá quitar de aquí.
 */
public record CreateReservationRequest(

        @NotBlank(message = "guestId es obligatorio")
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