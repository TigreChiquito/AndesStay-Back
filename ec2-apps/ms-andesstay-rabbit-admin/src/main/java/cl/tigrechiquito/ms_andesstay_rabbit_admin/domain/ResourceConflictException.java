package cl.tigrechiquito.ms_andesstay_rabbit_admin.domain;

/** Se mapea a HTTP 409: ya existe o está protegido. */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
