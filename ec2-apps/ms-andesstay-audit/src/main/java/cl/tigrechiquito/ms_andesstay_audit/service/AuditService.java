package cl.tigrechiquito.ms_andesstay_audit.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_audit.domain.AuditEvent;
import cl.tigrechiquito.ms_andesstay_audit.dto.AuditEventResponse;
import cl.tigrechiquito.ms_andesstay_audit.messaging.ReservationEventMessage;
import cl.tigrechiquito.ms_andesstay_audit.repository.AuditEventRepository;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Guarda el evento como una fila del historial. Idempotente por eventId: si
     * Kafka lo reentrega, se ignora.
     */
    @Transactional
    public void record(ReservationEventMessage event) {
        event.validate();
        if (repository.existsByEventId(event.eventId())) {
            log.info("Evento ya auditado (eventId={}), ignorado", event.eventId());
            return;
        }
        repository.save(new AuditEvent(
                event.eventId(),
                event.type(),
                event.reservationId(),
                event.unitId(),
                event.guestId(),
                event.status(),
                event.timestamp()));
        log.info("Auditado: reserva {} -> {} ({})", event.reservationId(), event.status(), event.type());
    }

    /** Línea de tiempo completa de una reserva, en orden cronológico. */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> timelineOf(Long reservationId) {
        return repository.findByReservationIdOrderByOccurredAtAsc(reservationId).stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    /** Últimos eventos registrados (todas las reservas). */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> recent(int limit) {
        return repository.findByOrderByOccurredAtDesc(PageRequest.of(0, limit)).stream()
                .map(AuditEventResponse::from)
                .toList();
    }
}