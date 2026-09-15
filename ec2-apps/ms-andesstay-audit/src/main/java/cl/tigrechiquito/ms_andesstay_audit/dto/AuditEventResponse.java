package cl.tigrechiquito.ms_andesstay_audit.dto;

import java.time.Instant;

import cl.tigrechiquito.ms_andesstay_audit.domain.AuditEvent;

public record AuditEventResponse(
        Long id,
        String eventId,
        String type,
        Long reservationId,
        Long unitId,
        String guestId,
        String status,
        Instant occurredAt,
        Instant recordedAt
) {

    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(
                e.getId(),
                e.getEventId(),
                e.getType(),
                e.getReservationId(),
                e.getUnitId(),
                e.getGuestId(),
                e.getStatus(),
                e.getOccurredAt(),
                e.getRecordedAt());
    }
}