package cl.tigrechiquito.ms_andesstay_audit.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Registro de auditoría: UNA fila por cada evento recibido (append-only).
 *
 * A diferencia de report (que mantiene un estado actual por reserva), audit NUNCA
 * actualiza ni borra: acumula. Así queda la línea de tiempo completa de qué pasó
 * con cada reserva y cuándo.
 *
 * El eventId es único: sirve para idempotencia (no guardar dos veces el mismo
 * evento si Kafka lo reentrega).
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private Long reservationId;

    private Long unitId;

    private String guestId;

    @Column(length = 20)
    private String status;

    /** Cuándo ocurrió el evento (timestamp del productor). */
    @Column(nullable = false)
    private Instant occurredAt;

    /** Cuándo audit lo registró. */
    @Column(nullable = false)
    private Instant recordedAt;

    protected AuditEvent() {
    }

    public AuditEvent(String eventId, String type, Long reservationId, Long unitId,
                      String guestId, String status, Instant occurredAt) {
        this.eventId = eventId;
        this.type = type;
        this.reservationId = reservationId;
        this.unitId = unitId;
        this.guestId = guestId;
        this.status = status;
        this.occurredAt = occurredAt;
        this.recordedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getType() {
        return type;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public String getGuestId() {
        return guestId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}