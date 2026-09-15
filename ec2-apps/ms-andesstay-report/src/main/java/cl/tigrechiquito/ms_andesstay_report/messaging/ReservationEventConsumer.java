package cl.tigrechiquito.ms_andesstay_report.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_report.domain.ReservationProjection;
import cl.tigrechiquito.ms_andesstay_report.repository.ReservationProjectionRepository;

/**
 * Consume reservations.events y mantiene actualizado el read model.
 *
 * El upsert es idempotente por naturaleza: reprocesar el mismo evento deja la
 * proyección igual. Además se descartan eventos más viejos que el último aplicado
 * (por si llegan desordenados o duplicados), comparando el timestamp.
 *
 * El group-id se define en application.yml (spring.kafka.consumer.group-id).
 */
@Component
public class ReservationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventConsumer.class);

    private final ReservationProjectionRepository repository;

    public ReservationEventConsumer(ReservationProjectionRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_RESERVATIONS_EVENTS)
    @Transactional
    public void onEvent(ReservationEventMessage event) {
        ReservationProjection projection = repository.findById(event.reservationId())
                .orElseGet(() -> new ReservationProjection(event.reservationId()));

        // Descarta eventos atrasados: si ya aplicamos uno más nuevo, ignorar.
        if (projection.getLastEventAt() != null
                && event.timestamp().isBefore(projection.getLastEventAt())) {
            log.info("Evento atrasado ignorado (reserva {}, {})",
                    event.reservationId(), event.type());
            return;
        }

        projection.setUnitId(event.unitId());
        projection.setCheckInDate(event.checkInDate());
        projection.setCheckOutDate(event.checkOutDate());
        projection.setStatus(event.status());
        projection.setLastEventAt(event.timestamp());
        repository.save(projection);

        log.info("Proyección actualizada: reserva {} -> {}", event.reservationId(), event.status());
    }
}