package cl.tigrechiquito.ms_andesstay_reservations.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CatalogClientTest {

    MockRestServiceServer server;
    CatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogClient(builder, "http://catalog");
    }

    // ---------- P1-2: unidad borrada en catalog ----------

    @Test
    void releaseDeUnaUnidadQueNoExisteCuentaComoExito() {
        server.expect(requestTo("http://catalog/api/units/9/release"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatCode(() -> client.releaseSlot(9L)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void reserveDeUnaUnidadQueNoExisteSigueSiendoError() {
        server.expect(requestTo("http://catalog/api/units/9/reserve"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.reserveSlot(9L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reserveDeUnaUnidadInactivaEsConflicto() {
        server.expect(requestTo("http://catalog/api/units/9/reserve"))
                .andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.reserveSlot(9L)).isInstanceOf(UnitNotAvailableException.class);
    }

    // ---------- P1-3: consulta de la unidad ----------

    @Test
    void getUnitLeeLaUnidadDeCatalog() {
        server.expect(requestTo("http://catalog/api/units/3"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"id":3,"name":"Lodge","active":false,"pricePerNight":80000.00,"totalSlots":2}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.getUnit(3L).active()).isFalse();
    }

    @Test
    void getUnitDeUnaUnidadQueNoExisteEsError400() {
        server.expect(requestTo("http://catalog/api/units/3"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.getUnit(3L)).isInstanceOf(UnitNotFoundInCatalogException.class);
    }
}
