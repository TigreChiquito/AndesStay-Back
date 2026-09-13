package cl.tigrechiquito.ms_andesstay_catalog.domain;

import java.text.Normalizer;
import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Tipo de unidad de hospedaje de la red AndesStay.
 *
 * Identificadores sin ñ ni tildes (ASCII) para evitar problemas de encoding,
 * pero el {@code @JsonCreator} acepta la entrada con ñ/acentos: "CABAÑA" se
 * normaliza a CABANA.
 */
public enum UnitType {

    HOSTAL,
    CABANA,
    LODGE;

    @JsonCreator
    public static UnitType fromJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")   // ñ -> n, á -> a, etc.
                .trim()
                .toUpperCase();
        try {
            return UnitType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Tipo de unidad inválido: '" + value + "'. Valores permitidos: "
                            + Arrays.toString(values()));
        }
    }
}