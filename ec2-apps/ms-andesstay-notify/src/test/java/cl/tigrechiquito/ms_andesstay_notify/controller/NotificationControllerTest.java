package cl.tigrechiquito.ms_andesstay_notify.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.tigrechiquito.ms_andesstay_notify.service.NotificationService;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    NotificationService service;

    // ---------- P2-5: contador de no leídas ----------

    @Test
    void huespedRecibeSuContador() throws Exception {
        when(service.countUnread("A", false)).thenReturn(3L);

        mvc.perform(get("/api/notifications/unread-count").header("X-User-Id", "A").header("X-User-Roles", "Cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void personalCuentaTambienLosAvisosDelPersonal() throws Exception {
        when(service.countUnread("R", true)).thenReturn(7L);

        mvc.perform(get("/api/notifications/unread-count").header("X-User-Id", "R").header("X-User-Roles", "Recepcionista"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(7));
    }
}
