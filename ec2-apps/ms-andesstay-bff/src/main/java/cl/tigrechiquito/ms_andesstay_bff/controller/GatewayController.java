package cl.tigrechiquito.ms_andesstay_bff.controller;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import cl.tigrechiquito.ms_andesstay_bff.config.GatewayProperties;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Gateway simple: toda petición /api/** que pasó la seguridad se reenvía al micro
 * correspondiente según el primer segmento de la ruta.
 *
 *   /api/reservations/**  -> reservations
 *   /api/units/**         -> catalog (clave "units")
 *   /api/reports/**       -> report  (clave "reports")
 *   /api/audit/**         -> audit
 *
 * Es un proxy de paso didáctico (reenvía método, query, body y content-type).
 * Para producción se usaría un gateway real; acá basta para el flujo del caso.
 */
@RestController
public class GatewayController {

    private final RestClient restClient;
    private final GatewayProperties properties;

    public GatewayController(GatewayProperties properties) {
        this.restClient = RestClient.create();
        this.properties = properties;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                        @RequestBody(required = false) byte[] body) {

        String baseUrl = resolveBaseUrl(request.getRequestURI());
        if (baseUrl == null) {
            return ResponseEntity.notFound().build();
        }

        String targetUrl = baseUrl + request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        RestClient.RequestBodySpec spec = restClient
                .method(HttpMethod.valueOf(request.getMethod()))
                .uri(URI.create(targetUrl));

        String contentType = request.getContentType();
        if (contentType != null) {
            spec = spec.header(HttpHeaders.CONTENT_TYPE, contentType);
        }
        if (body != null && body.length > 0) {
            spec = spec.body(body);
        }

        try {
            ResponseEntity<byte[]> downstream = spec.retrieve()
                    // no lanzar excepción por 4xx/5xx: dejamos pasar la respuesta tal cual
                    .onStatus(HttpStatusCode::isError, (req, res) -> { })
                    .toEntity(byte[].class);

            MediaType type = downstream.getHeaders().getContentType();
            return ResponseEntity.status(downstream.getStatusCode())
                    .contentType(type != null ? type : MediaType.APPLICATION_JSON)
                    .body(downstream.getBody());

        } catch (ResourceAccessException ex) {
            // el micro de destino no responde
            return ResponseEntity.status(502).build();
        }
    }

    /** Del path /api/{segmento}/... saca el segmento y busca su URL base. */
    private String resolveBaseUrl(String uri) {
        String[] parts = uri.split("/");
        if (parts.length < 3) {
            return null;
        }
        return properties.getRoutes().get(parts[2]);
    }
}