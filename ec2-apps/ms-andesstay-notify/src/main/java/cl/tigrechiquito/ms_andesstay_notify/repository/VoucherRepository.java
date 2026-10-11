package cl.tigrechiquito.ms_andesstay_notify.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.tigrechiquito.ms_andesstay_notify.domain.Voucher;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    boolean existsByReservationId(Long reservationId);

    Optional<Voucher> findByReservationId(Long reservationId);
}
