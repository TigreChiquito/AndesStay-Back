package cl.tigrechiquito.ms_andesstay_report.messaging;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Copia del evento que emite reservations en reservations.events. Los nombres de
 * los campos deben calzar con el JSON del productor. Campos que report no usa
 * (como guestId) se dejan igual por fidelidad, pero no molestan.
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