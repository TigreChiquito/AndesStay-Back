package cl.tigrechiquito.ms_andesstay_rabbit_admin.client;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.client.ManagementApiModels.BindingJson;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.client.ManagementApiModels.ExchangeJson;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.client.ManagementApiModels.NodeJson;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.client.ManagementApiModels.QueueJson;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.config.AdminProperties;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ManagementUnavailableException;

/**
 * Cliente de solo lectura de la API HTTP de la Management UI (puerto 15672).
 * RabbitAdmin (AMQP) no puede listar la topología, así que los listados y las
 * métricas salen de aquí. Siempre sobre el vhost "/" (%2F).
 */
@Component
public class ManagementClient {

    private static final Logger log = LoggerFactory.getLogger(ManagementClient.class);
    private static final int TIMEOUT_MS = 5000;
    private static final String VHOST = "%2F";

    private final RestClient restClient;
    private final String baseUrl;

    public ManagementClient(AdminProperties properties) {
        AdminProperties.Management m = properties.management();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.baseUrl = m.url().replaceAll("/+$", "");
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(m.username(), m.password()))
                .build();
    }

    public List<QueueJson> queues() {
        return get("/api/queues/" + VHOST, new ParameterizedTypeReference<>() { });
    }

    public List<ExchangeJson> exchanges() {
        return get("/api/exchanges/" + VHOST, new ParameterizedTypeReference<>() { });
    }

    public List<BindingJson> bindings() {
        return get("/api/bindings/" + VHOST, new ParameterizedTypeReference<>() { });
    }

    public List<NodeJson> nodes() {
        return get("/api/nodes", new ParameterizedTypeReference<>() { });
    }

    private <T> List<T> get(String path, ParameterizedTypeReference<List<T>> type) {
        try {
            // URI ya armada: el %2F del vhost no debe re-codificarse
            List<T> body = restClient.get().uri(URI.create(baseUrl + path)).retrieve().body(type);
            return body != null ? body : List.of();
        } catch (RestClientException ex) {
            // La URL (IP privada) y la causa van al log, no a la respuesta.
            log.warn("No se pudo consultar la Management UI en {}{}", baseUrl, path, ex);
            throw new ManagementUnavailableException(
                    "No se pudo consultar la Management UI de RabbitMQ. Intenta de nuevo en unos minutos.", ex);
        }
    }
}
