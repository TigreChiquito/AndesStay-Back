package cl.tigrechiquito.ms_andesstay_reservations.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente REST hacia ms-andesstay-catalog para coordinar la disponibilidad de
 * cupos. Es la integración síncrona que evita el overbooking: al confirmar una
 * reserva, primero se descuenta el cupo en catalog; si no hay, la confirmación
 * se aborta.
 */
@Component
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder,
                         @Value("${catalog.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    /** Descuenta un cupo. Lanza UnitNotAvailableException si catalog responde 409. */
    public void reserveSlot(Long unitId) {
        call(unitId, "reserve");
    }

    /** Devuelve un cupo al pool. */
    public void releaseSlot(Long unitId) {
        call(unitId, "release");
    }

    private void call(Long unitId, String action) {
        try {
            restClient.post()
                    .uri("/api/units/{id}/{action}", unitId, action)
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            int code = ex.getStatusCode().value();
            if (code == 409) {
                throw new UnitNotAvailableException(unitId);
            }
            if (code == 404) {
                throw new IllegalArgumentException(
                        "La unidad " + unitId + " no existe en el catálogo");
            }
            throw ex;
        } catch (ResourceAccessException ex) {
            throw new CatalogUnavailableException(unitId, ex);
        }
    }
}