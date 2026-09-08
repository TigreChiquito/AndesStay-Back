package cl.tigrechiquito.ms_andesstay_reservations.domain;

/**
 * Se lanza cuando se pide una reserva que no existe. Se mapea a HTTP 404.
 */
public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(Long id) {
        super("No existe la reserva con id " + id);
    }
}