package cl.tigrechiquito.ms_andesstay_report.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read model (proyección) que report mantiene a partir de los eventos.
 *
 * Guarda el estado ACTUAL de cada reserva. Sobre esta tabla se calculan los KPIs
 * (contar por estado, por unidad, etc.) sin tener que sumar/restar a mano: basta
 * con consultar. Es el patrón CQRS: reservations es el lado de escritura, report
 * mantiene su propia vista de solo lectura.
 *
 * El id NO es autogenerado: es el mismo reservationId que viene en el evento.
 */
@Entity
@Table(name = "reservation_projection")
public class ReservationProjection {

    @Id
    private Long reservationId;

    private Long unitId;

    private LocalDate checkInDate;

    private LocalDate checkOutDate;

    @Column(length = 20)
    private String status;

    /** Timestamp del último evento aplicado (para descartar eventos atrasados). */
    private Instant lastEventAt;

    protected ReservationProjection() {
    }

    public ReservationProjection(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Long getUnitId() {
        return unitId;
    }

    public void setUnitId(Long unitId) {
        this.unitId = unitId;
    }

    public LocalDate getCheckInDate() {
        return checkInDate;
    }

    public void setCheckInDate(LocalDate checkInDate) {
        this.checkInDate = checkInDate;
    }

    public LocalDate getCheckOutDate() {
        return checkOutDate;
    }

    public void setCheckOutDate(LocalDate checkOutDate) {
        this.checkOutDate = checkOutDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }
}