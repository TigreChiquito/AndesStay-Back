package cl.tigrechiquito.ms_andesstay_notify.messaging;

import java.time.LocalDate;

/**
 * Datos de la reserva que llegan en el comando. Los nombres de los campos deben
 * calzar con los que envía reservations (ReservationCommand).
 */
public record NotificationPayload(
        Long reservationId,
        String guestId,
        String guestName,
        Long unitId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String status
) {
}