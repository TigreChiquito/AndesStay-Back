package cl.tigrechiquito.ms_andesstay_reservations.dto;

import java.time.Instant;
import java.time.LocalDate;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;

/**
 * Representación de salida de una reserva. No exponemos la entidad JPA
 * directamente para no filtrar detalles internos (version, lazy proxies, etc.).
 */
public record ReservationResponse(
        Long id,
        String guestId,
        String guestName,
        Long unitId,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        ReservationStatus status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getGuestId(),
                r.getGuestName(),
                r.getUnitId(),
                r.getCheckInDate(),
                r.getCheckOutDate(),
                r.getStatus(),
                r.getCreatedAt(),
                r.getUpdatedAt()
        );
    }
}