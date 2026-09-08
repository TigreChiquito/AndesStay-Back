package cl.tigrechiquito.ms_andesstay_catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Unidad de hospedaje (hostal, cabaña o lodge) del catálogo.
 *
 * La disponibilidad ({@code availableSlots}) se maneja como modelo de dominio
 * rico: se descuenta con {@link #reserveOne()} y se devuelve con
 * {@link #releaseOne()}, sin dejar que nadie modifique el contador a mano.
 * reservations llama a estos métodos (vía REST) al CONFIRMAR o CANCELAR.
 */
@Entity
@Table(name = "units")
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UnitType type;

    /** Comuna o dirección de la unidad (Región Metropolitana). */
    private String location;

    /** Cupo total de la unidad (no cambia con las reservas). */
    @Column(nullable = false)
    private int totalSlots;

    /** Cupos actualmente disponibles. Baja al confirmar, sube al cancelar. */
    @Column(nullable = false)
    private int availableSlots;

    /** Tarifa por noche en CLP. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    /** Si está activa, se puede reservar y aparece en el catálogo. */
    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected Unit() {
    }

    public Unit(String name, UnitType type, String location,
                int totalSlots, BigDecimal pricePerNight) {
        this.name = name;
        this.type = type;
        this.location = location;
        this.totalSlots = totalSlots;
        this.availableSlots = totalSlots; // nace con todos los cupos libres
        this.pricePerNight = pricePerNight;
        this.active = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Descuenta un cupo (al confirmar una reserva).
     *
     * @throws NoAvailabilityException si no quedan cupos disponibles.
     */
    public void reserveOne() {
        if (availableSlots <= 0) {
            throw new NoAvailabilityException(this.id);
        }
        this.availableSlots--;
    }

    /**
     * Devuelve un cupo (al cancelar una reserva). Nunca supera el cupo total.
     */
    public void releaseOne() {
        if (availableSlots < totalSlots) {
            this.availableSlots++;
        }
    }

    public boolean hasAvailability() {
        return availableSlots > 0;
    }

    // --- Getters ---

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UnitType getType() {
        return type;
    }

    public String getLocation() {
        return location;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    // --- Setters de datos editables (el catálogo los edita vía PUT) ---
    // availableSlots NO tiene setter: solo cambia por reserveOne()/releaseOne().

    public void setName(String name) {
        this.name = name;
    }

    public void setType(UnitType type) {
        this.type = type;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public void setPricePerNight(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Ajusta el cupo total (ej. la unidad amplía capacidad). Mantiene coherente
     * la disponibilidad: si sube el total, se suman esos cupos a los disponibles.
     */
    public void adjustTotalSlots(int newTotal) {
        int delta = newTotal - this.totalSlots;
        this.totalSlots = newTotal;
        this.availableSlots = Math.max(0, this.availableSlots + delta);
    }
}