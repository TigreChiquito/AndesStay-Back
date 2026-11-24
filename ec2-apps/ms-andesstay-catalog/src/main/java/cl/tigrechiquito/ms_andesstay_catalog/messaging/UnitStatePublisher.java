package cl.tigrechiquito.ms_andesstay_catalog.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publica el estado de la unidad en units.state DESPUÉS del commit (nunca un estado
 * que luego se revierta). key = unitId: la compactación se queda con el último valor
 * por unidad y todos los cambios de una unidad caen en la misma partición, en orden.
 *
 * Un fallo de Kafka no debe romper la operación de catálogo (ya está guardada):
 * se registra en el log y se sigue, igual que en reservations.
 */
@Component
public class UnitStatePublisher {

    private static final Logger log = LoggerFactory.getLogger(UnitStatePublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public UnitStatePublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUnitChanged(UnitChangedEvent event) {
        UnitStateMessage message = UnitStateMessage.of(event.unit());
        try {
            kafka.send(KafkaTopicConfig.TOPIC_UNITS_STATE, String.valueOf(message.unitId()), message)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("No se pudo publicar el estado de la unidad {} en Kafka",
                                    message.unitId(), ex);
                        }
                    });
        } catch (Exception ex) {
            log.error("Kafka no disponible: el estado de la unidad {} no se publicó", message.unitId(), ex);
        }
    }
}
