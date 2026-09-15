package cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit;

import static cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit.RabbitConstants.EXCHANGE_TOPIC;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Publica los comandos de la reserva hacia el exchange topic (cmd.topic).
 * Cada método usa una routing key que calza con el patrón de su cola:
 *   email.*          <- email.confirmation / email.reminder / email.checkout
 *   voucher.*        <- voucher.gen
 *   housekeeping.#   <- housekeeping.ticket
 */
@Component
public class ReservationCommandPublisher {

    private final RabbitTemplate rabbit;

    public ReservationCommandPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void emailConfirmation(Reservation r) {
        publish("email.confirmation", r);
    }

    public void emailCheckinReminder(Reservation r) {
        publish("email.reminder", r);
    }

    public void emailCheckout(Reservation r) {
        publish("email.checkout", r);
    }

    public void voucher(Reservation r) {
        publish("voucher.gen", r);
    }

    public void housekeepingTicket(Reservation r) {
        publish("housekeeping.ticket", r);
    }

    private void publish(String type, Reservation r) {
        CommandEnvelope<ReservationCommand> envelope = CommandEnvelope.create(
                type,
                "reservation-" + r.getId(),
                ReservationCommand.from(r));
        rabbit.convertAndSend(EXCHANGE_TOPIC, type, envelope);
    }
}