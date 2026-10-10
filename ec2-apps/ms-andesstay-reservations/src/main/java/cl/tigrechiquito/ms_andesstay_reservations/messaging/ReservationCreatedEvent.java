package cl.tigrechiquito.ms_andesstay_reservations.messaging;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Evento interno de Spring que se publica al crear una reserva. Lo consume el
 * KafkaEventListener (AFTER_COMMIT) para emitir el evento a reservations.events.
 * También lo escucha el listener de RabbitMQ (aviso al huésped y al personal).
 */
public record ReservationCreatedEvent(Reservation reservation) {
}