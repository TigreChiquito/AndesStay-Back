package cl.tigrechiquito.ms_andesstay_catalog.domain;

/**
 * Se lanza al intentar reservar un cupo de una unidad desactivada.
 * Se mapea a HTTP 409 (Conflict).
 */
public class UnitInactiveException extends RuntimeException {

    public UnitInactiveException(Long unitId) {
        super("La unidad " + unitId + " está desactivada y no acepta reservas");
    }
}
