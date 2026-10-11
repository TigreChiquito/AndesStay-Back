package cl.tigrechiquito.ms_andesstay_notify.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Voucher de una reserva confirmada. Guarda los datos con que se emitió y el PDF
 * ya generado (bytea), así descargarlo no depende de que catalog esté arriba.
 * Uno por reserva: reservationId es único.
 */
@Entity
@Table(name = "vouchers")
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long reservationId;

    /** Código legible para el huésped, ej. AS-000007. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** oid del huésped (= guestId de la reserva). Define quién puede descargarlo. */
    private String guestId;

    private String guestName;

    private Long unitId;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    /** Total de la estadía; null si catalog no respondió al emitirlo. */
    @Column(precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private byte[] pdf;

    @Column(nullable = false)
    private Instant createdAt;

    protected Voucher() {
        // JPA
    }

    public Voucher(Long reservationId, String code, String guestId, String guestName, Long unitId,
                   LocalDate checkInDate, LocalDate checkOutDate, BigDecimal total, byte[] pdf) {
        this.reservationId = reservationId;
        this.code = code;
        this.guestId = guestId;
        this.guestName = guestName;
        this.unitId = unitId;
        this.checkInDate = checkInDate;
        this.checkOutDate = checkOutDate;
        this.total = total;
        this.pdf = pdf;
        this.createdAt = Instant.now();
    }

    /** El huésped solo ve el suyo; el personal (Recepcionista/Admin), cualquiera. */
    public boolean isVisibleTo(String userId, boolean staff) {
        return staff || (guestId != null && guestId.equals(userId));
    }

    // --- Getters ---

    public Long getId() {
        return id;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getCode() {
        return code;
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

    public BigDecimal getTotal() {
        return total;
    }

    public byte[] getPdf() {
        return pdf;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
