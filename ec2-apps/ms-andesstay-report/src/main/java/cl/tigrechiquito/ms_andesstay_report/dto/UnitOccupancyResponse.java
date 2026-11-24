package cl.tigrechiquito.ms_andesstay_report.dto;

import java.math.BigDecimal;

/** Ocupación actual de una unidad; occupancyRate va de 0 a 1. */
public record UnitOccupancyResponse(
        Long unitId,
        String name,
        String type,
        int totalSlots,
        int availableSlots,
        double occupancyRate,
        BigDecimal pricePerNight,
        boolean active
) {
}
