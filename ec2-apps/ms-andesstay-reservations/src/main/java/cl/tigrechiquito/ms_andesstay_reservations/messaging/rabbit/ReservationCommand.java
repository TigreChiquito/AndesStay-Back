package cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit;

import java.time.LocalDate;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Snapshot de la reserva que viaja como payload de los comandos a RabbitMQ.
 * Lleva lo que notify necesita para armar el email, el voucher o el ticket de
 * housekeeping, sin exponer la entidad JPA.
 */
public record ReservationCommand(
        Long reservationId,
        String guestId,
        String guestName,
        Long unitId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        String status
) {

    public static ReservationCommand from(Reservation r) {
        return new ReservationCommand(
                r.getId(),
                r.getGuestId(),
                r.getGuestName(),
                r.getUnitId(),
                r.getCheckInDate(),
                r.getCheckOutDate(),
                r.getStatus().name());
    }
}