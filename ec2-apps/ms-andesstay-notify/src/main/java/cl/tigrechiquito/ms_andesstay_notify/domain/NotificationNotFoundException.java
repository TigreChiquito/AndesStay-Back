package cl.tigrechiquito.ms_andesstay_notify.domain;

/**
 * La notificación no existe o no es visible para el usuario (no se distingue a
 * propósito, para no revelar avisos ajenos). Se mapea a HTTP 404.
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(Long id) {
        super("No existe la notificación con id " + id);
    }
}
