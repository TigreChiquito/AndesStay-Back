package cl.tigrechiquito.ms_andesstay_reservations.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Lo que reservations necesita de una unidad de catalog (GET /api/units/{id}). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogUnit(Long id, boolean active) {
}
