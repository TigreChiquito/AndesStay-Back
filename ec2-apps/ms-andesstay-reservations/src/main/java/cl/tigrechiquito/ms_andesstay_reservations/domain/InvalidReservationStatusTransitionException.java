package cl.tigrechiquito.ms_andesstay_reservations.domain;

/**
 * Se lanza cuando se intenta una transición de estado que la máquina de
 * estados no permite (por ejemplo, hacer check-in de una reserva que no fue
 * confirmada, o modificar una reserva ya en CHECKOUT / CANCELADA).
 *
 * Es una RuntimeException para poder mapearla luego en un
 * {@code @RestControllerAdvice} a un HTTP 409 (Conflict) o 422.
 */
public class InvalidReservationStatusTransitionException extends RuntimeException {

    private final ReservationStatus from;
    private final ReservationStatus to;

    public InvalidReservationStatusTransitionException(ReservationStatus from, ReservationStatus to) {
        super("Transición de estado no permitida: " + from + " -> " + to);
        this.from = from;
        this.to = to;
    }

    public ReservationStatus getFrom() {
        return from;
    }

    public ReservationStatus getTo() {
        return to;
    }
}