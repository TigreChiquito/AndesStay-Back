package cl.tigrechiquito.ms_andesstay_reservations.messaging;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Evento interno de Spring que se publica al crear una reserva. Lo consume el
 * KafkaEventListener (AFTER_COMMIT) para emitir el evento a reservations.events.
 * No lo escucha el listener de RabbitMQ: al crear no se notifica nada.
 */
public record ReservationCreatedEvent(Reservation reservation) {
}