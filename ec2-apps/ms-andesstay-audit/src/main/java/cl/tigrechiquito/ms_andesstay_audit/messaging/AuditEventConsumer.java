package cl.tigrechiquito.ms_andesstay_audit.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_audit.domain.AuditEvent;
import cl.tigrechiquito.ms_andesstay_audit.repository.AuditEventRepository;

/**
 * Consume reservations.events y guarda cada uno como una fila del historial.
 *
 * Idempotencia por eventId: si el evento ya fue registrado (reentrega de Kafka),
 * se ignora. Es un consumer group distinto al de report, así que ambos reciben
 * TODOS los eventos de forma independiente (fan-out).
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditEventRepository repository;

    public AuditEventConsumer(AuditEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = KafkaConstants.TOPIC_RESERVATIONS_EVENTS)
    @Transactional
    public void onEvent(ReservationEventMessage event) {
        if (repository.existsByEventId(event.eventId())) {
            log.info("Evento ya auditado (eventId={}), ignorado", event.eventId());
            return;
        }

        AuditEvent record = new AuditEvent(
                event.eventId(),
                event.type(),
                event.reservationId(),
                event.unitId(),
                event.guestId(),
                event.status(),
                event.timestamp());

        repository.save(record);
        log.info("Auditado: reserva {} -> {} ({})",
                event.reservationId(), event.status(), event.type());
    }
}