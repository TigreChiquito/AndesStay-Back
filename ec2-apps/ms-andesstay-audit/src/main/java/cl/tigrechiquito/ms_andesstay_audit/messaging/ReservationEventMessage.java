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

    /** Sin estos campos el evento no se puede auditar: va directo a la DLT, sin reintentos. */
    public void validate() {
        if (eventId == null || reservationId == null || type == null || timestamp == null) {
            throw new IllegalArgumentException(
                    "Evento de reserva incompleto: eventId, reservationId, type y timestamp son obligatorios");
        }
    }
}
