package cl.tigrechiquito.ms_andesstay_catalog.domain;

/**
 * Se lanza al bajar el cupo total por debajo de los cupos ocupados.
 * Se mapea a HTTP 409 (Conflict).
 */
public class SlotsInUseException extends RuntimeException {

    public SlotsInUseException(int occupied) {
        super("Hay " + occupied + " cupos ocupados; el total no puede ser menor.");
    }
}
