package cl.tigrechiquito.ms_andesstay_reservations.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;

/**
 * Acceso a datos de reservas.
 *
 * Las queries derivadas de abajo cubren el endpoint de la pauta:
 *   GET /api/reservations?status=...&from=...&to=...
 *
 * Cuando los filtros sean todos opcionales conviene migrar a Specifications
 * o a una @Query con parámetros nulos; por ahora, derivadas alcanzan.
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByStatus(ReservationStatus status);

    List<Reservation> findByCheckInDateBetween(LocalDate from, LocalDate to);

    List<Reservation> findByStatusAndCheckInDateBetween(
            ReservationStatus status, LocalDate from, LocalDate to);
}