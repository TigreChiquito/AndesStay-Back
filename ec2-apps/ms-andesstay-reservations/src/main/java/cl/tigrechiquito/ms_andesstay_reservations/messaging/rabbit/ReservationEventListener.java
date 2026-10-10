package cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit;

import static cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit.ReservationCommandPublisher.*;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.messaging.ReservationCreatedEvent;
import cl.tigrechiquito.ms_andesstay_reservations.messaging.ReservationStatusChangedEvent;

/**
 * Reacciona a la creación y a los cambios de estado de una reserva, y encola los
 * comandos que correspondan en RabbitMQ:
 *
 *   Estado              Huésped (notification.*)   Personal (housekeeping.#)
 *   CREADA              created                    new_reservation
 *   CONFIRMADA          confirmed  (+ voucher.gen)  —
 *   CHECKIN_PENDIENTE   —                          prepare_unit
 *   EN_ESTADIA          —                          —
 *   CHECKOUT            checkout                   clean_unit
 *   CANCELADA           cancelled                  reservation_cancelled
 *
 * Usa {@code AFTER_COMMIT}: los comandos se envían solo si la transacción de la
 * reserva se confirmó de verdad. Así evitamos el bug clásico de notificar una
 * confirmación que después hace rollback, y además el cambio de estado NO falla
 * si RabbitMQ está caído (se persiste igual; el envío es un paso aparte).
 */
@Component
public class ReservationEventListener {

    private final ReservationCommandPublisher publisher;

    public ReservationEventListener(ReservationCommandPublisher publisher) {
        this.publisher = publisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(ReservationCreatedEvent event) {
        Reservation r = event.reservation();
        publisher.publish(NOTIFICATION_CREATED, r);
        publisher.publish(HOUSEKEEPING_NEW_RESERVATION, r);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStatusChanged(ReservationStatusChangedEvent event) {
        Reservation r = event.reservation();

        switch (r.getStatus()) {
            case CONFIRMADA -> {
                publisher.publish(NOTIFICATION_CONFIRMED, r);
                publisher.publish(VOUCHER_GEN, r);
            }
            case CHECKIN_PENDIENTE -> publisher.publish(HOUSEKEEPING_PREPARE_UNIT, r);
            case CHECKOUT -> {
                publisher.publish(NOTIFICATION_CHECKOUT, r);
                publisher.publish(HOUSEKEEPING_CLEAN_UNIT, r);
            }
            case CANCELADA -> {
                publisher.publish(NOTIFICATION_CANCELLED, r);
                publisher.publish(HOUSEKEEPING_RESERVATION_CANCELLED, r);
            }
            default -> {
                // CREADA (se notifica en onCreated) / EN_ESTADIA: sin aviso
            }
        }
    }
}
