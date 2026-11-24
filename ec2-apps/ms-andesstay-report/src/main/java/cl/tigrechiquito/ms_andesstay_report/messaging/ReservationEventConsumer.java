package cl.tigrechiquito.ms_andesstay_report.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.tigrechiquito.ms_andesstay_report.service.ProjectionService;

/**
 * Adaptador de Kafka: recibe reservations.events y delega en el dominio. Corre en
 * el grupo ms-andesstay-report (application.yml), distinto del de audit: los dos
 * reciben todos los eventos (pub/sub). Con concurrency 3, un hilo por partición.
 */
@Component
public class ReservationEventConsumer {

    private final ProjectionService projections;

    public ReservationEventConsumer(ProjectionService projections) {
        this.projections = projections;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_RESERVATIONS_EVENTS)
    public void onEvent(ReservationEventMessage event) {
        projections.applyReservation(event);
    }
}
