package cl.tigrechiquito.ms_andesstay_report.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_report.dto.ReportSummaryResponse;
import cl.tigrechiquito.ms_andesstay_report.dto.UnitCountResponse;
import cl.tigrechiquito.ms_andesstay_report.service.ReportService;

/**
 * API de reportería (solo lectura). Lee del read model que mantiene el consumidor.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** GET /api/reports/summary — resumen general de KPIs. */
    @GetMapping("/summary")
    public ReportSummaryResponse summary() {
        return service.summary();
    }

    /** GET /api/reports/by-status — cantidad de reservas por estado. */
    @GetMapping("/by-status")
    public Map<String, Long> byStatus() {
        return service.byStatus();
    }

    /** GET /api/reports/by-unit — cantidad de reservas por unidad. */
    @GetMapping("/by-unit")
    public List<UnitCountResponse> byUnit() {
        return service.byUnit();
    }
}