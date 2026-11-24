package cl.tigrechiquito.ms_andesstay_report.messaging;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Copia del mensaje que emite catalog en units.state (estado completo de la unidad).
 */
public record UnitStateMessage(
        Long unitId,
        String name,
        String type,
        String location,
        int totalSlots,
        int availableSlots,
        BigDecimal pricePerNight,
        boolean active,
        Instant timestamp
) {

    /** Un estado imposible no se aplica: va directo a la DLT, sin reintentos. */
    public void validate() {
        if (unitId == null || timestamp == null) {
            throw new IllegalArgumentException("Estado de unidad incompleto: unitId y timestamp son obligatorios");
        }
        if (totalSlots < 0 || availableSlots < 0 || availableSlots > totalSlots) {
            throw new IllegalArgumentException("Cupos inválidos para la unidad " + unitId
                    + ": disponibles=" + availableSlots + ", total=" + totalSlots);
        }
    }
}
