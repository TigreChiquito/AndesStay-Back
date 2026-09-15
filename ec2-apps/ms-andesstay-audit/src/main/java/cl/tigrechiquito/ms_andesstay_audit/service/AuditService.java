package cl.tigrechiquito.ms_andesstay_audit.service;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_audit.dto.AuditEventResponse;
import cl.tigrechiquito.ms_andesstay_audit.repository.AuditEventRepository;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
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