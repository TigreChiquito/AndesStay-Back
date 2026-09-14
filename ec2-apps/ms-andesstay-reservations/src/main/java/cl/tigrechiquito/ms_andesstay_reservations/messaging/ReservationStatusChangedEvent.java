package cl.tigrechiquito.ms_andesstay_reservations.messaging;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;

/**
 * Evento interno de Spring que se publica cuando una reserva cambia de estado.
 * Lo consume {@link ReservationEventListener} DESPUÉS de que la transacción
 * hace commit, para recién ahí mandar los comandos a RabbitMQ.
 */
public record ReservationStatusChangedEvent(Reservation reservation) {
}