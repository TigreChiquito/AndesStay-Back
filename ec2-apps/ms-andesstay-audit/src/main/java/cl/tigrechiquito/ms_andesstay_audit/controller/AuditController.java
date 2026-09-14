package cl.tigrechiquito.ms_andesstay_audit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_audit.dto.AuditEventResponse;
import cl.tigrechiquito.ms_andesstay_audit.service.AuditService;

/**
 * API de auditoría (solo lectura). El rol Auditor consulta acá la trazabilidad.
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** GET /api/audit/reservations/{id} — línea de tiempo de una reserva. */
    @GetMapping("/reservations/{reservationId}")
    public List<AuditEventResponse> timeline(@PathVariable Long reservationId) {
        return service.timelineOf(reservationId);
    }

    /** GET /api/audit/events?limit=50 — últimos eventos registrados. */
    @GetMapping("/events")
    public List<AuditEventResponse> recent(
            @RequestParam(required = false, defaultValue = "50") int limit) {
        return service.recent(limit);
    }
}