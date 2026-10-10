package cl.tigrechiquito.ms_andesstay_reservations.domain;

/**
 * Se lanza cuando un huésped intenta operar sobre una reserva que no es suya.
 * Se mapea a HTTP 403.
 */
public class ReservationAccessDeniedException extends RuntimeException {

    public ReservationAccessDeniedException(Long id) {
        super("La reserva " + id + " no pertenece al usuario autenticado");
    }
}
