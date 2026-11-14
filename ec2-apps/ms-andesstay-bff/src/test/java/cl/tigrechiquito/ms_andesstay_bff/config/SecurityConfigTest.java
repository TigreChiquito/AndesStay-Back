package cl.tigrechiquito.ms_andesstay_bff.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.tigrechiquito.ms_andesstay_bff.controller.GatewayController;

/**
 * Reglas de autorización del BFF. Sin rutas configuradas, una petición que pasa
 * la seguridad termina en 404 (no hay micro al que reenviarla); una que no pasa, en 401/403.
 */
@WebMvcTest(GatewayController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "AZURE_TENANT_ID=test-tenant",
        "AZURE_API_CLIENT_ID=test-client"})
class SecurityConfigTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    GatewayProperties gatewayProperties;

    @MockitoBean
    JwtDecoder jwtDecoder;

    static JwtRequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void recentSoloParaElPersonal() throws Exception {
        mvc.perform(get("/api/reservations/recent").with(as("Cliente"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/recent").with(as("Auditor"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/reservations/recent").with(as("Recepcionista"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/reservations/recent").with(as("Admin"))).andExpect(status().isNotFound());
    }

    @Test
    void mineParaCualquierAutenticado() throws Exception {
        mvc.perform(get("/api/reservations/mine")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/reservations/mine").with(as("Cliente"))).andExpect(status().isNotFound());
    }
}
