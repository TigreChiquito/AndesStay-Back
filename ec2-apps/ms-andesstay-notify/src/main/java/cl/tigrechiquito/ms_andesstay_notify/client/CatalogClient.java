package cl.tigrechiquito.ms_andesstay_notify.client;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consulta a catalog los datos de la unidad para el voucher (nombre, ubicación,
 * tarifa). Es "best effort": si catalog no responde, el voucher se emite igual
 * con lo que trae el comando, así un catalog caído no manda el mensaje a la DLQ.
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);
    private static final int TIMEOUT_MS = 3000;

    private final RestClient restClient;

    public CatalogClient(@Value("${catalog.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public Optional<UnitInfo> findUnit(Long unitId) {
        if (unitId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/units/{id}", unitId)
                    .retrieve()
                    .body(UnitInfo.class));
        } catch (Exception ex) {
            log.warn("No se pudo consultar la unidad {} en catalog: {}", unitId, ex.getMessage());
            return Optional.empty();
        }
    }
}
