package cl.tigrechiquito.ms_andesstay_reservations.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        Instant updatedAt,
        List<ReservationStatus> allowedTransitions,  // estados a los que puede pasar
        ReservationStatus nextStatus,                // siguiente paso del flujo; null si es terminal
        boolean cancellable                          // allowedTransitions contiene CANCELADA
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
                r.getUpdatedAt(),
                List.copyOf(r.getStatus().allowedTransitions()),
                r.getStatus().nextStatus(),
                r.getStatus().canTransitionTo(ReservationStatus.CANCELADA)
        );
    }
}