package cl.tigrechiquito.ms_andesstay_reservations.messaging;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Reacciona al cambio de estado de una reserva y encola los comandos que
 * correspondan en RabbitMQ.
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
    public void onStatusChanged(ReservationStatusChangedEvent event) {
        Reservation r = event.reservation();

        switch (r.getStatus()) {
            case CONFIRMADA -> {
                publisher.emailConfirmation(r); // confirmación al huésped
                publisher.voucher(r);           // genera el voucher/boleta PDF
            }
            case CHECKIN_PENDIENTE -> {
                publisher.housekeepingTicket(r);   // avisa a housekeeping que prepare la unidad
                publisher.emailCheckinReminder(r); // recordatorio de check-in
            }
            case CHECKOUT -> publisher.emailCheckout(r);
            default -> {
                // CREADA / EN_ESTADIA / CANCELADA: sin notificación por ahora
            }
        }
    }
}