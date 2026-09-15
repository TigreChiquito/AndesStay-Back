package cl.tigrechiquito.ms_andesstay_catalog.domain;

/**
 * Se lanza al intentar reservar un cupo de una unidad que ya no tiene
 * disponibilidad. Se mapea a HTTP 409 (Conflict).
 */
public class NoAvailabilityException extends RuntimeException {

    public NoAvailabilityException(Long unitId) {
        super("La unidad " + unitId + " no tiene cupos disponibles");
    }
}