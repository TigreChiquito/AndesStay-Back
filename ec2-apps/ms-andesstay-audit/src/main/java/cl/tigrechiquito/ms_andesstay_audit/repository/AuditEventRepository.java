package cl.tigrechiquito.ms_andesstay_audit.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import cl.tigrechiquito.ms_andesstay_audit.domain.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** Idempotencia: ¿ya guardamos este evento? */
    boolean existsByEventId(String eventId);

    /** Línea de tiempo de una reserva, en orden cronológico. */
    List<AuditEvent> findByReservationIdOrderByOccurredAtAsc(Long reservationId);

    /** Últimos eventos registrados (para un listado general). */
    List<AuditEvent> findByOrderByOccurredAtDesc(Pageable pageable);
}