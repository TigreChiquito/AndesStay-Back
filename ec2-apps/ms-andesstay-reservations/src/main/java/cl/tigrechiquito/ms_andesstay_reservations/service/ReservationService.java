package cl.tigrechiquito.ms_andesstay_reservations.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationNotFoundException;
import cl.tigrechiquito.ms_andesstay_reservations.dto.CreateReservationRequest;
import cl.tigrechiquito.ms_andesstay_reservations.repository.ReservationRepository;

/**
 * Orquesta los casos de uso de reservas. La validación de la máquina de estados
 * NO vive aquí, sino dentro de la entidad ({@code Reservation.changeStatusTo}).
 * Este servicio se encarga de la persistencia, las reglas de aplicación y
 * (más adelante) de emitir los eventos/comandos de mensajería.
 */
@Service
public class ReservationService {

    private final ReservationRepository repository;

    public ReservationService(ReservationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Reservation create(CreateReservationRequest req) {
        if (!req.checkOutDate().isAfter(req.checkInDate())) {
            throw new IllegalArgumentException(
                    "checkOutDate debe ser posterior a checkInDate");
        }

        Reservation reservation = new Reservation(
                req.guestId(),
                req.guestName(),
                req.unitId(),
                req.checkInDate(),
                req.checkOutDate());

        Reservation saved = repository.save(reservation);

        // TODO (mensajería): publicar evento "reservation.created" en Kafka
        //   (tópico reservations.events) para alimentar auditoría y reportería.
        return saved;
    }

    @Transactional(readOnly = true)
    public Reservation getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    @Transactional
    public Reservation changeStatus(Long id, ReservationStatus target) {
        Reservation reservation = getById(id);

        // La entidad valida la transición y lanza
        // InvalidReservationStatusTransitionException si no corresponde.
        reservation.changeStatusTo(target);

        Reservation saved = repository.save(reservation);

        // TODO (mensajería):
        //  - publicar el cambio de estado en Kafka (reservations.events).
        //  - si target == CONFIRMADA -> encolar email de confirmación + voucher (RabbitMQ).
        //  - si target == CHECKIN_PENDIENTE/EN_ESTADIA -> ticket de housekeeping (RabbitMQ).
        //  - coordinar disponibilidad con ms-andesstay-catalog al CONFIRMAR.
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Reservation> search(ReservationStatus status, LocalDate from, LocalDate to) {
        boolean hasRange = (from != null && to != null);

        if (status != null && hasRange) {
            return repository.findByStatusAndCheckInDateBetween(status, from, to);
        }
        if (status != null) {
            return repository.findByStatus(status);
        }
        if (hasRange) {
            return repository.findByCheckInDateBetween(from, to);
        }
        return repository.findAll();
    }
}
