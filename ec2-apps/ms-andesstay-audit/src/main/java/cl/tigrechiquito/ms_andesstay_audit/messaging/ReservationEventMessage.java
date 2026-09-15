package cl.tigrechiquito.ms_andesstay_audit.messaging;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Copia del evento que emite reservations en reservations.events.
 * Los nombres de los campos calzan con el JSON del productor.
 */
public record ReservationEventMessage(
        String type,
        String eventId,
        Instant timestamp,
        Long reservationId,
        String guestId,
        Long unitId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String status
) {
}