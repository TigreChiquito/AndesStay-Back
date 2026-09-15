package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Evento que viaja por el tópico reservations.events (fuente de verdad).
 *
 * A diferencia de los comandos de RabbitMQ (que son órdenes "haz esto"), esto es
 * un evento de hecho consumado ("esto pasó"): report y audit lo leen para armar
 * KPIs y trazabilidad. Estructura plana (sin payload anidado) para que a los
 * consumidores les sea simple de leer.
 */
public record ReservationEventMessage(
        String type,           // "reservation.created" / "reservation.status_changed"
        String eventId,
        Instant timestamp,
        Long reservationId,
        String guestId,
        Long unitId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String status
) {

    public static ReservationEventMessage of(String type, Reservation r) {
        return new ReservationEventMessage(
                type,
                UUID.randomUUID().toString(),
                Instant.now(),
                r.getId(),
                r.getGuestId(),
                r.getUnitId(),
                r.getCheckInDate(),
                r.getCheckOutDate(),
                r.getStatus().name());
    }
}