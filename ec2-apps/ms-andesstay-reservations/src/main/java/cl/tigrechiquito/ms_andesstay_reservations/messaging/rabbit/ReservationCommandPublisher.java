package cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit;

import static cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit.RabbitConstants.EXCHANGE_TOPIC;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Publica los comandos de la reserva hacia el exchange topic (cmd.topic).
 * Cada routing key calza con el patrón de su cola:
 *   notification.*   <- avisos al huésped (q.cmd.notification)
 *   housekeeping.#   <- avisos al personal: Recepcionista/Admin (q.cmd.housekeeping)
 *   voucher.*        <- generación del voucher (q.cmd.voucher)
 */
@Component
public class ReservationCommandPublisher {

    // Avisos al huésped
    public static final String NOTIFICATION_CREATED = "notification.created";
    public static final String NOTIFICATION_CONFIRMED = "notification.confirmed";
    public static final String NOTIFICATION_CHECKOUT = "notification.checkout";
    public static final String NOTIFICATION_CANCELLED = "notification.cancelled";

    // Avisos al personal
    public static final String HOUSEKEEPING_NEW_RESERVATION = "housekeeping.new_reservation";
    public static final String HOUSEKEEPING_PREPARE_UNIT = "housekeeping.prepare_unit";
    public static final String HOUSEKEEPING_CLEAN_UNIT = "housekeeping.clean_unit";
    public static final String HOUSEKEEPING_RESERVATION_CANCELLED = "housekeeping.reservation_cancelled";

    public static final String VOUCHER_GEN = "voucher.gen";

    private static final Logger log = LoggerFactory.getLogger(ReservationCommandPublisher.class);

    private final RabbitTemplate rabbit;

    public ReservationCommandPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /**
     * Se llama AFTER_COMMIT: la reserva ya está guardada, así que un fallo de
     * RabbitMQ no debe convertirse en error HTTP. Se registra en el log y se sigue.
     */
    public void publish(String type, Reservation r) {
        CommandEnvelope<ReservationCommand> envelope = CommandEnvelope.create(
                type,
                "reservation-" + r.getId(),
                ReservationCommand.from(r));
        try {
            rabbit.convertAndSend(EXCHANGE_TOPIC, type, envelope);
        } catch (Exception ex) {
            log.error("RabbitMQ no disponible: comando {} de la reserva {} no se publicó",
                    type, r.getId(), ex);
        }
    }
}
