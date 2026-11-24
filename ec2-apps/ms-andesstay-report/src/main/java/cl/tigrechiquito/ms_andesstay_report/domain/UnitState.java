package cl.tigrechiquito.ms_andesstay_report.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read model del inventario: última foto de cada unidad recibida por units.state.
 * El id es el mismo unitId de catalog.
 */
@Entity
@Table(name = "unit_state")
public class UnitState {

    @Id
    private Long unitId;

    private String name;

    private String type;

    private int totalSlots;

    private int availableSlots;

    private BigDecimal pricePerNight;

    private boolean active;

    /** Timestamp de la última foto aplicada (para descartar fotos atrasadas). */
    private Instant lastEventAt;

    protected UnitState() {
    }

    public UnitState(Long unitId) {
        this.unitId = unitId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public int getTotalSlots() {
        return totalSlots;
    }

    public int getAvailableSlots() {
        return availableSlots;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    /** Reemplaza la foto completa (units.state trae siempre el estado entero). */
    public void apply(String name, String type, int totalSlots, int availableSlots,
                      BigDecimal pricePerNight, boolean active, Instant at) {
        this.name = name;
        this.type = type;
        this.totalSlots = totalSlots;
        this.availableSlots = availableSlots;
        this.pricePerNight = pricePerNight;
        this.active = active;
        this.lastEventAt = at;
    }
}
