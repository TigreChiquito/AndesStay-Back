package cl.tigrechiquito.ms_andesstay_catalog.messaging;

import java.math.BigDecimal;
import java.time.Instant;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;

/**
 * Estado completo de una unidad, tal como viaja por units.state. Es una foto (no
 * un delta): como el tópico es compactado, Kafka guarda solo la última por unitId
 * y quien lo lea desde el inicio reconstruye el inventario entero.
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

    public static UnitStateMessage of(Unit u) {
        return new UnitStateMessage(
                u.getId(),
                u.getName(),
                u.getType().name(),
                u.getLocation(),
                u.getTotalSlots(),
                u.getAvailableSlots(),
                u.getPricePerNight(),
                u.isActive(),
                Instant.now());
    }
}
