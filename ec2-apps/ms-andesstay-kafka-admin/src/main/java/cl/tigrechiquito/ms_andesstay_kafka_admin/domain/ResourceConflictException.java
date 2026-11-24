package cl.tigrechiquito.ms_andesstay_kafka_admin.domain;

/** Se mapea a HTTP 409: ya existe, está protegido o el grupo está activo. */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
