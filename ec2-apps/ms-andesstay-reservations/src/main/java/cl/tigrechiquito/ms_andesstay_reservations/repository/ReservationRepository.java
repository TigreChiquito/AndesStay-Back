package cl.tigrechiquito.ms_andesstay_reservations.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import jakarta.persistence.LockModeType;

/**
 * Acceso a datos de reservas. Los filtros opcionales de
 * GET /api/reservations se arman con Specifications (ver ReservationService.search).
 */
public interface ReservationRepository extends JpaRepository<Reservation, Long>,
        JpaSpecificationExecutor<Reservation> {

    /**
     * Lee la reserva bloqueando la fila (SELECT ... FOR UPDATE) hasta el fin de la
     * transacción: serializa los cambios de estado concurrentes de una misma reserva,
     * para que dos checkouts simultáneos no devuelvan dos cupos a catalog.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    List<Reservation> findByGuestIdOrderByCreatedAtDesc(String guestId);
}
