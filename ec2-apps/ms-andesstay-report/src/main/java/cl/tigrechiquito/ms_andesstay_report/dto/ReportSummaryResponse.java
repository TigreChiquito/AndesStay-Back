package cl.tigrechiquito.ms_andesstay_report.dto;

import java.util.List;
import java.util.Map;

/**
 * Resumen de indicadores del negocio.
 *
 * @param totalReservations total de reservas registradas
 * @param activeStays       reservas actualmente EN_ESTADIA (ocupación actual)
 * @param byStatus          cantidad por estado
 * @param byUnit            cantidad por unidad (de mayor a menor)
 */
public record ReportSummaryResponse(
        long totalReservations,
        long activeStays,
        Map<String, Long> byStatus,
        List<UnitCountResponse> byUnit
) {
}