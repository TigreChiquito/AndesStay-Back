package cl.tigrechiquito.ms_andesstay_reservations.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
 * Reserva de hospedaje. Modelo de dominio "rico": la máquina de estados vive
 * dentro de la entidad ({@link #changeStatusTo}) en lugar de estar dispersa en
 * el servicio, de modo que una Reservation nunca puede quedar en un estado
 * inconsistente.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identidad del huésped (sub/oid del token de Azure AD). */
    @Column(nullable = false)
    private String guestId;

    private String guestName;

    /** Id de la unidad en ms-andesstay-catalog. Sin FK: es otro microservicio. */
    @Column(nullable = false)
    private Long unitId;

    @Column(nullable = false)
    private LocalDate checkInDate;

    @Column(nullable = false)
    private LocalDate checkOutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /**
     * Tarifa por noche (CLP) al momento de reservar, copiada de catalog. Así el
     * voucher respeta lo que vio el huésped aunque después cambie la tarifa.
     * Nula en reservas creadas antes de guardar el precio.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal pricePerNight;

    /** pricePerNight × noches. Nulo si no hay pricePerNight. */
    @Column(precision = 14, scale = 2)
    private BigDecimal total;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /** Bloqueo optimista: protege contra actualizaciones concurrentes de estado. */
    @Version
    private Long version;

    /** Constructor sin argumentos requerido por JPA. No usar en el código. */
    protected Reservation() {
    }

    /**
     * Crea una reserva nueva. Toda reserva nace en {@link ReservationStatus#CREADA}.
     */
    public Reservation(String guestId, String guestName, Long unitId,
                       LocalDate checkInDate, LocalDate checkOutDate) {
        this.guestId = guestId;
        this.guestName = guestName;
        this.unitId = unitId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.status = ReservationStatus.CREADA;
    }

    /** Fija la tarifa por noche y calcula el total según las noches de la estadía. */
    public void applyPrice(BigDecimal pricePerNight) {
        this.pricePerNight = pricePerNight;
        this.total = pricePerNight == null
                ? null
                : pricePerNight.multiply(BigDecimal.valueOf(ChronoUnit.DAYS.between(checkInDate, checkOutDate)));
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
     * Cambia el estado respetando la máquina de estados.
     *
     * <p>Pedir el mismo estado actual es un no-op (idempotente), útil para que
     * un PUT repetido no falle. Cualquier otra transición no contemplada en
     * {@link ReservationStatus#allowedTransitions()} lanza excepción.
     *
     * @throws InvalidReservationStatusTransitionException si la transición no es válida.
     */
    public void changeStatusTo(ReservationStatus target) {
        if (target == null) {
            throw new InvalidReservationStatusTransitionException(this.status, null);
        }
        if (target == this.status) {
            return; // idempotente
        }
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidReservationStatusTransitionException(this.status, target);
        }
        this.status = target;
    }

    // --- Getters ---

    public Long getId() {
        return id;
    }

    public String getGuestId() {
        return guestId;
    }

    public String getGuestName() {
        return guestName;
    }

    public Long getUnitId() {
        return unitId;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public BigDecimal getTotal() {
        return total;
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

    // --- Setters de datos editables ---
    // El estado NO tiene setter: solo se cambia vía changeStatusTo().

    public void setGuestName(String guestName) {
        this.guestName = guestName;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }
}