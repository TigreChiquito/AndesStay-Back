package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica los eventos de la reserva en el tópico reservations.events.
 *
 * Usa el reservationId como key del mensaje: así todos los eventos de una misma
 * reserva caen en la misma partición y mantienen el orden entre sí (importante
 * para que audit reconstruya bien la línea de tiempo).
 */
@Component
public class ReservationEventPublisher {

    private final KafkaTemplate<String, Object> kafka;

    public ReservationEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void publish(ReservationEventMessage event) {
        kafka.send(
                KafkaConstants.TOPIC_RESERVATIONS_EVENTS,
                String.valueOf(event.reservationId()),  // key = id de la reserva
                event);
    }
}