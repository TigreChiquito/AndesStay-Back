package cl.tigrechiquito.ms_andesstay_notify.client;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que el voucher necesita de una unidad de catalog (el resto del JSON se ignora). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UnitInfo(
        Long id,
        String name,
        String type,
        String location,
        BigDecimal pricePerNight
) {
}
