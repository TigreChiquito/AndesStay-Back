package cl.tigrechiquito.ms_andesstay_bff.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mapa "segmento de la URL -> URL base del micro". Se llena desde application.yml
 * (prefijo gateway.routes). Ej: units -> http://localhost:8082
 */
@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Map<String, String> routes = new HashMap<>();

    public Map<String, String> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, String> routes) {
        this.routes = routes;
    }
}