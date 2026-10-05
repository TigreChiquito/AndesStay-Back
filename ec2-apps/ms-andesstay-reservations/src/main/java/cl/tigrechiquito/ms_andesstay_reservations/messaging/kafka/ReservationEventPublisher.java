package cl.tigrechiquito.ms_andesstay_reservations.messaging.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public ReservationEventPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    /**
     * Se llama AFTER_COMMIT: la reserva ya está guardada, así que un fallo de Kafka
     * no debe convertirse en error HTTP. Se registra en el log y se sigue.
     */
    public void publish(ReservationEventMessage event) {
        try {
            kafka.send(
                    KafkaConstants.TOPIC_RESERVATIONS_EVENTS,
                    String.valueOf(event.reservationId()),  // key = id de la reserva
                    event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("No se pudo publicar {} de la reserva {} en Kafka",
                                    event.type(), event.reservationId(), ex);
                        }
                    });
        } catch (Exception ex) {
            log.error("Kafka no disponible: {} de la reserva {} no se publicó",
                    event.type(), event.reservationId(), ex);
        }
    }
}