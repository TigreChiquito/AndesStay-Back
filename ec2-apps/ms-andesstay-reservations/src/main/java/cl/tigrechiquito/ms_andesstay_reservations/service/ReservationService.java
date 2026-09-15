package cl.tigrechiquito.ms_andesstay_reservations.service;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_reservations.domain.InvalidReservationStatusTransitionException;
import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationNotFoundException;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
import cl.tigrechiquito.ms_andesstay_reservations.dto.CreateReservationRequest;
import cl.tigrechiquito.ms_andesstay_reservations.messaging.ReservationCreatedEvent;
import cl.tigrechiquito.ms_andesstay_reservations.messaging.ReservationStatusChangedEvent;
import cl.tigrechiquito.ms_andesstay_reservations.repository.ReservationRepository;

/**
 * Orquesta los casos de uso de reservas. La maquina de estados vive en la entidad;
 * este servicio coordina persistencia, mensajeria (eventos AFTER_COMMIT) y la
 * integracion sincrona con catalog para la disponibilidad de cupos.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository repository;
    private final ApplicationEventPublisher events;
    private final CatalogClient catalogClient;

    public ReservationService(ReservationRepository repository,
                              ApplicationEventPublisher events,
                              CatalogClient catalogClient) {
        this.repository = repository;
        this.events = events;
        this.catalogClient = catalogClient;
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

        // El KafkaEventListener emite "reservation.created" a Kafka AFTER_COMMIT.
        events.publishEvent(new ReservationCreatedEvent(saved));
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
        ReservationStatus current = reservation.getStatus();

        // 1. Validar la transicion ANTES de tocar catalog (no reservar cupo en vano).
        if (target != current && !current.canTransitionTo(target)) {
            throw new InvalidReservationStatusTransitionException(current, target);
        }

        // 2. Coordinar disponibilidad con catalog (sincrono).
        //    Confirmar descuenta un cupo; si no hay, catalog responde 409 y abortamos.
        boolean slotReserved = false;
        if (target == ReservationStatus.CONFIRMADA && current != ReservationStatus.CONFIRMADA) {
            catalogClient.reserveSlot(reservation.getUnitId());
            slotReserved = true;
        } else if (releasesSlot(current, target)) {
            catalogClient.releaseSlot(reservation.getUnitId());
        }

        // 3. Aplicar y persistir. saveAndFlush fuerza el flush aqui para detectar
        //    fallos (ej. choque optimista) y poder compensar el cupo reservado.
        try {
            reservation.changeStatusTo(target);
            Reservation saved = repository.saveAndFlush(reservation);
            events.publishEvent(new ReservationStatusChangedEvent(saved));
            return saved;
        } catch (RuntimeException ex) {
            if (slotReserved) {
                compensateReserve(reservation.getUnitId());
            }
            throw ex;
        }
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

    /** El cupo se devuelve al cancelar una reserva ya confirmada o al hacer checkout. */
    private boolean releasesSlot(ReservationStatus current, ReservationStatus target) {
        if (target == ReservationStatus.CHECKOUT) {
            return true; // la estadia termino: el cupo vuelve al pool
        }
        if (target == ReservationStatus.CANCELADA) {
            return current == ReservationStatus.CONFIRMADA
                    || current == ReservationStatus.CHECKIN_PENDIENTE;
        }
        return false;
    }

    /** Best-effort: devuelve el cupo si el guardado local fallo tras reservarlo. */
    private void compensateReserve(Long unitId) {
        try {
            catalogClient.releaseSlot(unitId);
            log.warn("Compensacion: cupo devuelto en catalog (unidad {}) tras fallo local", unitId);
        } catch (RuntimeException ex) {
            log.error("No se pudo compensar el cupo de la unidad {} en catalog", unitId, ex);
        }
    }
}