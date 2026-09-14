package cl.tigrechiquito.ms_andesstay_report.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_report.dto.ReportSummaryResponse;
import cl.tigrechiquito.ms_andesstay_report.dto.UnitCountResponse;
import cl.tigrechiquito.ms_andesstay_report.repository.ReservationProjectionRepository;

@Service
public class ReportService {

    private static final String STATUS_EN_ESTADIA = "EN_ESTADIA";

    private final ReservationProjectionRepository repository;

    public ReportService(ReservationProjectionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse summary() {
        long total = repository.count();
        Map<String, Long> byStatus = byStatus();
        long activeStays = byStatus.getOrDefault(STATUS_EN_ESTADIA, 0L);
        return new ReportSummaryResponse(total, activeStays, byStatus, byUnit());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> byStatus() {
        Map<String, Long> result = new LinkedHashMap<>();
        repository.countGroupedByStatus()
                .forEach(row -> result.put(row.getStatus(), row.getTotal()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<UnitCountResponse> byUnit() {
        return repository.countGroupedByUnit().stream()
                .map(row -> new UnitCountResponse(row.getUnitId(), row.getTotal()))
                .toList();
    }
}