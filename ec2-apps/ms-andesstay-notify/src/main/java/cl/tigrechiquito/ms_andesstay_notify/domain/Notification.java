package cl.tigrechiquito.ms_andesstay_notify.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Aviso in-app generado a partir de un comando de RabbitMQ. Una fila por comando:
 * el eventId del CommandEnvelope es único, así una reentrega no duplica el aviso.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient", columnList = "audience, recipient_id")
})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Audience audience;

    /** oid del huésped si audience = GUEST; null si es para el personal. */
    @Column(name = "recipient_id")
    private String recipientId;

    /** Tipo de comando que la originó (ej. notification.confirmed). */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    private Long reservationId;

    private Long unitId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public Notification(String eventId, Audience audience, String recipientId, String type,
                        String title, String message, Long reservationId, Long unitId) {
        this.eventId = eventId;
        this.audience = audience;
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.reservationId = reservationId;
        this.unitId = unitId;
        this.read = false;
        this.createdAt = Instant.now();
    }

    /** ¿Puede verla este usuario? Huésped: solo las suyas. Personal: además las STAFF. */
    public boolean isVisibleTo(String userId, boolean staff) {
        if (audience == Audience.STAFF) {
            return staff;
        }
        return recipientId != null && recipientId.equals(userId);
    }

    public void markRead() {
        this.read = true;
    }

    // --- Getters ---

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Audience getAudience() {
        return audience;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
