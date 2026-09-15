package cl.tigrechiquito.ms_andesstay_catalog.domain;

/**
 * Se lanza cuando se pide una unidad que no existe. Se mapea a HTTP 404.
 */
public class UnitNotFoundException extends RuntimeException {

    public UnitNotFoundException(Long id) {
        super("No existe la unidad con id " + id);
    }
}