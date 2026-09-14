package cl.tigrechiquito.ms_andesstay_reservations.client;

/**
 * catalog respondió 409 al intentar descontar un cupo: no hay disponibilidad.
 * Se mapea a HTTP 409 hacia el cliente (la confirmación no procede).
 */
public class UnitNotAvailableException extends RuntimeException {

    public UnitNotAvailableException(Long unitId) {
        super("La unidad " + unitId + " no tiene cupos disponibles para confirmar la reserva");
    }
}