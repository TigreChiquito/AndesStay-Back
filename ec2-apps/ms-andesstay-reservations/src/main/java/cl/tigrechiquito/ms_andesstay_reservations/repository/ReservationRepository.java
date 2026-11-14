package cl.tigrechiquito.ms_andesstay_reservations.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
import jakarta.persistence.LockModeType;

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

    /**
     * Lee la reserva bloqueando la fila (SELECT ... FOR UPDATE) hasta el fin de la
     * transacción: serializa los cambios de estado concurrentes de una misma reserva,
     * para que dos checkouts simultáneos no devuelvan dos cupos a catalog.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    List<Reservation> findByGuestIdOrderByCreatedAtDesc(String guestId);

    List<Reservation> findByStatus(ReservationStatus status);

    List<Reservation> findByCheckInDateBetween(LocalDate from, LocalDate to);

    List<Reservation> findByStatusAndCheckInDateBetween(
            ReservationStatus status, LocalDate from, LocalDate to);
}