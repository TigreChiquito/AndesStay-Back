package cl.tigrechiquito.ms_andesstay_reservations.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogUnit;
import cl.tigrechiquito.ms_andesstay_reservations.domain.InvalidReservationStatusTransitionException;
import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationAccessDeniedException;
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
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

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

    /** Crea la reserva a nombre de {@code guestId} (lo decide el controller según quién llama). */
    @Transactional
    public Reservation create(CreateReservationRequest req, String guestId) {
        if (!req.checkOutDate().isAfter(req.checkInDate())) {
            throw new IllegalArgumentException(
                    "checkOutDate debe ser posterior a checkInDate");
        }
        // "Hoy" según Chile, no según la JVM (el contenedor corre en UTC).
        if (req.checkInDate().isBefore(LocalDate.now(ZONE))) {
            throw new IllegalArgumentException("La fecha de llegada no puede ser anterior a hoy.");
        }
        CatalogUnit unit = catalogClient.getUnit(req.unitId());
        if (!unit.active()) {
            throw new IllegalArgumentException(
                    "La unidad " + req.unitId() + " está desactivada y no acepta reservas.");
        }

        Reservation reservation = new Reservation(
                guestId,
                req.guestName(),
                req.unitId(),
                req.checkInDate(),
                req.checkOutDate());
        reservation.applyPrice(unit.pricePerNight());

        Reservation saved = repository.save(reservation);

        // El KafkaEventListener emite "reservation.created" a Kafka AFTER_COMMIT.
        events.publishEvent(new ReservationCreatedEvent(saved));
        return saved;
    }

    /**
     * Cancela una reserva a pedido de un usuario. El personal (Recepcionista/Admin)
     * puede cancelar cualquiera; un huésped solo las suyas (guestId == userId).
     * El resto (transición válida, devolución de cupo, eventos) es el mismo
     * flujo de changeStatus.
     */
    @Transactional
    public Reservation cancel(Long id, String userId, boolean staff) {
        Reservation reservation = getForUpdate(id);
        if (!staff && (userId == null || !userId.equals(reservation.getGuestId()))) {
            throw new ReservationAccessDeniedException(id);
        }
        return changeStatus(id, ReservationStatus.CANCELADA);
    }

    @Transactional(readOnly = true)
    public Reservation getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    /**
     * Lectura de una reserva respetando la visibilidad: quien no puede ver todas
     * solo ve las suyas. Una ajena responde 404 (no 403) para no revelar que existe.
     */
    @Transactional(readOnly = true)
    public Reservation getVisibleById(Long id, String userId, boolean readAll) {
        Reservation reservation = getById(id);
        if (!readAll && (userId == null || !userId.equals(reservation.getGuestId()))) {
            throw new ReservationNotFoundException(id);
        }
        return reservation;
    }

    /** Reservas del huésped, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<Reservation> findByGuest(String guestId) {
        return repository.findByGuestIdOrderByCreatedAtDesc(guestId);
    }

    @Transactional
    public Reservation changeStatus(Long id, ReservationStatus target) {
        Reservation reservation = getForUpdate(id);
        ReservationStatus current = reservation.getStatus();

        // 1. Validar la transicion ANTES de tocar catalog (no reservar cupo en vano).
        if (target != current && !current.canTransitionTo(target)) {
            throw new InvalidReservationStatusTransitionException(current, target);
        }

        // Mismo estado: idempotente. Sin esto se re-publicaban eventos/avisos y
        // un CHECKOUT repetido devolvía otro cupo a catalog.
        if (target == current) {
            return reservation;
        }

        // 2. Coordinar disponibilidad con catalog (sincrono).
        //    Confirmar descuenta un cupo; si no hay, catalog responde 409 y abortamos.
        boolean slotReserved = false;
        boolean slotReleased = false;
        if (target == ReservationStatus.CONFIRMADA && current != ReservationStatus.CONFIRMADA) {
            catalogClient.reserveSlot(reservation.getUnitId());
            slotReserved = true;
        } else if (releasesSlot(current, target)) {
            catalogClient.releaseSlot(reservation.getUnitId());
            slotReleased = true;
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
            if (slotReleased) {
                compensateRelease(reservation.getUnitId());
            }
            throw ex;
        }
    }

    /**
     * Búsqueda con filtros opcionales e independientes: {@code from}/{@code to}
     * acotan la fecha de llegada y {@code checkOutFrom}/{@code checkOutTo} la de
     * salida (ambos extremos inclusive). Si {@code guestId} no es nulo, solo
     * devuelve reservas de ese huésped (lo usa el controller para quien no es personal).
     */
    @Transactional(readOnly = true)
    public List<Reservation> search(ReservationStatus status, LocalDate from, LocalDate to,
                                    LocalDate checkOutFrom, LocalDate checkOutTo, String guestId) {
        List<Specification<Reservation>> filters = new ArrayList<>();
        if (guestId != null) {
            filters.add((r, q, cb) -> cb.equal(r.get("guestId"), guestId));
        }
        if (status != null) {
            filters.add((r, q, cb) -> cb.equal(r.get("status"), status));
        }
        if (from != null) {
            filters.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("checkInDate"), from));
        }
        if (to != null) {
            filters.add((r, q, cb) -> cb.lessThanOrEqualTo(r.get("checkInDate"), to));
        }
        if (checkOutFrom != null) {
            filters.add((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("checkOutDate"), checkOutFrom));
        }
        if (checkOutTo != null) {
            filters.add((r, q, cb) -> cb.lessThanOrEqualTo(r.get("checkOutDate"), checkOutTo));
        }
        return repository.findAll(Specification.allOf(filters), Sort.by(Sort.Direction.DESC, "createdAt"));
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

    /** Lee la reserva con bloqueo de fila; ver {@link ReservationRepository#findByIdForUpdate}. */
    private Reservation getForUpdate(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    /** Best-effort: vuelve a tomar el cupo si el guardado local fallo tras liberarlo. */
    private void compensateRelease(Long unitId) {
        try {
            catalogClient.reserveSlot(unitId);
            log.warn("Compensacion: cupo vuelto a tomar en catalog (unidad {}) tras fallo local", unitId);
        } catch (RuntimeException ex) {
            log.error("No se pudo compensar la liberacion del cupo de la unidad {} en catalog", unitId, ex);
        }
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