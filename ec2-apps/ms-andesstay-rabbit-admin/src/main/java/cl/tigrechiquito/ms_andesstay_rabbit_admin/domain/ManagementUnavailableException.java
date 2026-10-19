package cl.tigrechiquito.ms_andesstay_rabbit_admin.domain;

/** Se mapea a HTTP 503: la API de la Management UI no responde. */
public class ManagementUnavailableException extends RuntimeException {

    public ManagementUnavailableException(String message) {
        super(message);
    }

    public ManagementUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
