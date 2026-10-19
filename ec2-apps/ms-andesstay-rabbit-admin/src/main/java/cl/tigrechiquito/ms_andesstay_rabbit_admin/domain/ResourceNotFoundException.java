package cl.tigrechiquito.ms_andesstay_rabbit_admin.domain;

/** Se mapea a HTTP 404: no existe. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
