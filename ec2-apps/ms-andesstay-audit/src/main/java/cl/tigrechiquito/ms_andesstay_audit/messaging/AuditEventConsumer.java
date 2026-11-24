package cl.tigrechiquito.ms_andesstay_audit.messaging;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import cl.tigrechiquito.ms_andesstay_audit.service.AuditService;

/**
 * Adaptador de Kafka: recibe reservations.events y delega en AuditService.
 * Grupo ms-andesstay-audit, distinto al de report: ambos reciben TODOS los
 * eventos (pub/sub). Con concurrency 3, un hilo por partición.
 */
@Component
public class AuditEventConsumer {

    private final AuditService audit;

    public AuditEventConsumer(AuditService audit) {
        this.audit = audit;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_RESERVATIONS_EVENTS)
    public void onEvent(ReservationEventMessage event) {
        audit.record(event);
    }
}
