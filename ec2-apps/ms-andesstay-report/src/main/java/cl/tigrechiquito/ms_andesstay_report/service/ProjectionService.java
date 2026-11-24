package cl.tigrechiquito.ms_andesstay_report.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_report.domain.ReservationProjection;
import cl.tigrechiquito.ms_andesstay_report.domain.UnitState;
import cl.tigrechiquito.ms_andesstay_report.messaging.ReservationEventMessage;
import cl.tigrechiquito.ms_andesstay_report.messaging.UnitStateMessage;
import cl.tigrechiquito.ms_andesstay_report.repository.ReservationProjectionRepository;
import cl.tigrechiquito.ms_andesstay_report.repository.UnitStateRepository;

/**
 * Mantiene los read models de report a partir de los eventos (lógica de dominio,
 * sin nada de Kafka). Ambos upserts son idempotentes y descartan eventos más
 * viejos que el último aplicado, por si llegan duplicados o desordenados.
 */
@Service
public class ProjectionService {

    private static final Logger log = LoggerFactory.getLogger(ProjectionService.class);

    private final ReservationProjectionRepository reservations;
    private final UnitStateRepository units;

    public ProjectionService(ReservationProjectionRepository reservations, UnitStateRepository units) {
        this.reservations = reservations;
        this.units = units;
    }

    @Transactional
    public void applyReservation(ReservationEventMessage event) {
        event.validate();
        ReservationProjection projection = reservations.findById(event.reservationId())
                .orElseGet(() -> new ReservationProjection(event.reservationId()));

        if (projection.getLastEventAt() != null && event.timestamp().isBefore(projection.getLastEventAt())) {
            log.info("Evento atrasado ignorado (reserva {}, {})", event.reservationId(), event.type());
            return;
        }

        projection.setUnitId(event.unitId());
        projection.setCheckInDate(event.checkInDate());
        projection.setCheckOutDate(event.checkOutDate());
        projection.setStatus(event.status());
        projection.setLastEventAt(event.timestamp());
        reservations.save(projection);
        log.info("Proyección actualizada: reserva {} -> {}", event.reservationId(), event.status());
    }

    @Transactional
    public void applyUnitState(UnitStateMessage state) {
        state.validate();
        UnitState unit = units.findById(state.unitId()).orElseGet(() -> new UnitState(state.unitId()));

        if (unit.getLastEventAt() != null && state.timestamp().isBefore(unit.getLastEventAt())) {
            log.info("Estado atrasado ignorado (unidad {})", state.unitId());
            return;
        }

        unit.apply(state.name(), state.type(), state.totalSlots(), state.availableSlots(),
                state.pricePerNight(), state.active(), state.timestamp());
        units.save(unit);
        log.info("Inventario actualizado: unidad {} -> {}/{} cupos libres",
                state.unitId(), state.availableSlots(), state.totalSlots());
    }
}
