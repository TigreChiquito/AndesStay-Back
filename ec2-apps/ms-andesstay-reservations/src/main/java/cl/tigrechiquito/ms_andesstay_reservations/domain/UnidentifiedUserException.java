package cl.tigrechiquito.ms_andesstay_reservations.domain;

/**
 * La petición no trae X-User-Id (el BFF siempre lo propaga para usuarios
 * autenticados). Se mapea a HTTP 401.
 */
public class UnidentifiedUserException extends RuntimeException {

    public UnidentifiedUserException() {
        super("No pudimos identificar tu usuario. Vuelve a iniciar sesión.");
    }
}
