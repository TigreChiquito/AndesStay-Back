package cl.tigrechiquito.ms_andesstay_reservations.client;

/**
 * No se pudo contactar a catalog (caído o inaccesible).
 * Se mapea a HTTP 503 hacia el cliente.
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(Long unitId, Throwable cause) {
        super("No se pudo contactar al catálogo para la unidad " + unitId, cause);
    }
}