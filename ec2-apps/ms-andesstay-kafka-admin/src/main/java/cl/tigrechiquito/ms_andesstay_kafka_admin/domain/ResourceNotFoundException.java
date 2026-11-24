package cl.tigrechiquito.ms_andesstay_kafka_admin.domain;

/** Se mapea a HTTP 404: no existe. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
