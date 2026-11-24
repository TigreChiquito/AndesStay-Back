package cl.tigrechiquito.ms_andesstay_report.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.tigrechiquito.ms_andesstay_report.service.ProjectionService;

/**
 * Adaptador de Kafka: recibe units.state y delega en el dominio.
 *
 * El value-deserializer por defecto (application.yml) apunta a ReservationEventMessage;
 * este listener lo sobreescribe con su propio tipo.
 */
@Component
public class UnitStateConsumer {

    private final ProjectionService projections;

    public UnitStateConsumer(ProjectionService projections) {
        this.projections = projections;
    }

    @KafkaListener(
            topics = KafkaConstants.TOPIC_UNITS_STATE,
            groupId = KafkaConstants.GROUP_INVENTORY,
            properties = "spring.json.value.default.type=cl.tigrechiquito.ms_andesstay_report.messaging.UnitStateMessage")
    public void onState(UnitStateMessage state) {
        projections.applyUnitState(state);
    }
}
