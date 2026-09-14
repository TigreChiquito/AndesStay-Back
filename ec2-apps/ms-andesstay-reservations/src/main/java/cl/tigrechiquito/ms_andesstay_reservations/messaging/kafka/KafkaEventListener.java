package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.tigrechiquito.ms_andesstay_reservations.messaging.ReservationCreatedEvent;
import cl.tigrechiquito.ms_andesstay_reservations.messaging.ReservationStatusChangedEvent;

/**
 * Emite los eventos a Kafka (reservations.events) DESPUÉS del commit, tanto al
 * crear la reserva como en cada cambio de estado. Es un listener aparte del de
 * RabbitMQ: cada transporte tiene su propia responsabilidad.
 *
 * A diferencia de RabbitMQ (solo reacciona a algunos estados), a Kafka va TODO,
 * porque es la fuente de verdad que alimenta report y audit.
 */
@Component
public class KafkaEventListener {

    private final ReservationEventPublisher publisher;

    public KafkaEventListener(ReservationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(ReservationCreatedEvent event) {
        publisher.publish(ReservationEventMessage.of("reservation.created", event.reservation()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ReservationStatusChangedEvent event) {
        publisher.publish(ReservationEventMessage.of("reservation.status_changed", event.reservation()));
    }
}