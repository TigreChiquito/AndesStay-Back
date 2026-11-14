package cl.tigrechiquito.ms_andesstay_audit.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.tigrechiquito.ms_andesstay_audit.service.AuditService;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditService service;

    // ---------- P1-6: límite de /events ----------

    @Test
    void limiteFueraDeRangoResponde400() throws Exception {
        for (String limit : new String[] {"0", "-1", "501", "10000000"}) {
            mvc.perform(get("/api/audit/events").param("limit", limit))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("El límite debe estar entre 1 y 500."));
        }
        verifyNoInteractions(service);
    }

    @Test
    void limiteValidoConsultaLosEventos() throws Exception {
        mvc.perform(get("/api/audit/events").param("limit", "500")).andExpect(status().isOk());
        mvc.perform(get("/api/audit/events")).andExpect(status().isOk());

        verify(service).recent(500);
        verify(service).recent(50);
    }
}
