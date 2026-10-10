package cl.tigrechiquito.ms_andesstay_notify.domain;

/**
 * A quién va dirigida una notificación.
 *
 *   GUEST -> un huésped puntual (recipientId = su oid de Azure AD = guestId de la reserva)
 *   STAFF -> bandeja compartida del personal (la ven Recepcionista y Admin)
 */
public enum Audience {
    GUEST,
    STAFF
}
