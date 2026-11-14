package cl.tigrechiquito.ms_andesstay_rabbit_admin.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.config.AdminProperties;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ManagementUnavailableException;

class ManagementClientTest {

    @Test
    void elErrorNoExponeLaDireccionDeLaManagementUi() {
        // Puerto 1 en loopback: conexión rechazada al instante, sin red externa.
        ManagementClient client = new ManagementClient(new AdminProperties(
                new AdminProperties.Management("http://127.0.0.1:1", "u", "p", "http://public:15672"),
                List.of("cmd.")));

        assertThatThrownBy(client::nodes)
                .isInstanceOf(ManagementUnavailableException.class)
                .hasMessageNotContaining("127.0.0.1")
                .hasMessageNotContaining(":1");
    }
}
