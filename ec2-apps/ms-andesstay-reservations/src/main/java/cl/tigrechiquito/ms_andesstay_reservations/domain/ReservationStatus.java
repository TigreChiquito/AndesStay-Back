package cl.tigrechiquito.ms_andesstay_reservations.domain;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Estados del ciclo de vida de una reserva de hospedaje y las transiciones
 * permitidas entre ellos (máquina de estados).
 *
 * Flujo feliz:
 *   CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE -> EN_ESTADIA -> CHECKOUT
 *
 * CANCELADA es un estado terminal alternativo, alcanzable solo mientras la
 * estadía no haya comenzado (CREADA, CONFIRMADA o CHECKIN_PENDIENTE).
 *
 * Regla de la pauta ("no se puede hacer check-in sin CONFIRMAR") queda
 * garantizada por la tabla de transiciones: CHECKIN_PENDIENTE solo es
 * alcanzable desde CONFIRMADA, y EN_ESTADIA (check-in efectivo) solo desde
 * CHECKIN_PENDIENTE.
 */
public enum ReservationStatus {

    CREADA,
    CONFIRMADA,
    CHECKIN_PENDIENTE,
    EN_ESTADIA,
    CHECKOUT,
    CANCELADA;

    /**
     * Estados a los que se puede transitar desde este.
     * Un conjunto vacío indica un estado terminal.
     */
    public Set<ReservationStatus> allowedTransitions() {
        return switch (this) {
            case CREADA            -> EnumSet.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA        -> EnumSet.of(CHECKIN_PENDIENTE, CANCELADA);
            case CHECKIN_PENDIENTE -> EnumSet.of(EN_ESTADIA, CANCELADA);
            case EN_ESTADIA        -> EnumSet.of(CHECKOUT);
            case CHECKOUT, CANCELADA -> EnumSet.noneOf(ReservationStatus.class);
        };
    }

    /** ¿Se puede pasar de este estado a {@code target}? */
    public boolean canTransitionTo(ReservationStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    /** ¿Es un estado final (sin salidas)? */
    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }

    /**
     * Deserialización tolerante desde JSON. Acepta la variante con tilde de la
     * pauta ("EN_ESTADÍA"), minúsculas y espacios: normaliza quitando acentos y
     * pasando a mayúsculas antes de resolver el enum.
     */
    @JsonCreator
    public static ReservationStatus fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")   // Í -> I, á -> a, etc.
                .trim()
                .toUpperCase();
        try {
            return ReservationStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Estado inválido: '" + value + "'. Valores permitidos: "
                            + Arrays.toString(values()));
        }
    }
}
