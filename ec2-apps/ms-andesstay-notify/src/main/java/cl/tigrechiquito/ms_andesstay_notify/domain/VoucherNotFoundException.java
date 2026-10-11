package cl.tigrechiquito.ms_andesstay_notify.domain;

/**
 * No hay voucher para la reserva, o no es visible para el usuario (no se distingue
 * a propósito, para no revelar reservas ajenas). Se mapea a HTTP 404.
 */
public class VoucherNotFoundException extends RuntimeException {

    public VoucherNotFoundException(Long reservationId) {
        super("No hay voucher para la reserva " + reservationId);
    }
}
