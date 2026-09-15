package cl.tigrechiquito.ms_andesstay_catalog.dto;

import java.math.BigDecimal;
import java.time.Instant;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitType;

public record UnitResponse(
        Long id,
        String name,
        UnitType type,
        String location,
        int totalSlots,
        int availableSlots,
        BigDecimal pricePerNight,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static UnitResponse from(Unit u) {
        return new UnitResponse(
                u.getId(),
                u.getName(),
                u.getType(),
                u.getLocation(),
                u.getTotalSlots(),
                u.getAvailableSlots(),
                u.getPricePerNight(),
                u.isActive(),
                u.getCreatedAt(),
                u.getUpdatedAt()
        );
    }
}