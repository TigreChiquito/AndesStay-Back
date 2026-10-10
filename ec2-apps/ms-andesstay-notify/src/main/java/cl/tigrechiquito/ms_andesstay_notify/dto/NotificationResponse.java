package cl.tigrechiquito.ms_andesstay_notify.dto;

import java.time.Instant;

import cl.tigrechiquito.ms_andesstay_notify.domain.Audience;
import cl.tigrechiquito.ms_andesstay_notify.domain.Notification;

public record NotificationResponse(
        Long id,
        Audience audience,
        String type,
        String title,
        String message,
        Long reservationId,
        Long unitId,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getAudience(),
                n.getType(),
                n.getTitle(),
                n.getMessage(),
                n.getReservationId(),
                n.getUnitId(),
                n.isRead(),
                n.getCreatedAt());
    }
}
