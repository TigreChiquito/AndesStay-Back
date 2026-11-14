package cl.tigrechiquito.ms_andesstay_catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitType;
import cl.tigrechiquito.ms_andesstay_catalog.repository.UnitRepository;
import cl.tigrechiquito.ms_andesstay_catalog.service.CatalogService;

/** Controller + service reales; el repositorio se simula (sin Postgres). */
@WebMvcTest(UnitController.class)
@Import(CatalogService.class)
class UnitControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UnitRepository repository;

    static Unit unit() {
        return new Unit("Lodge Andino", UnitType.LODGE, "Farellones", 3, new BigDecimal("80000"));
    }

    // ---------- P1-2: borrado lógico ----------

    @Test
    void deleteDesactivaLaUnidadSinBorrarLaFila() throws Exception {
        Unit u = unit();
        when(repository.findById(1L)).thenReturn(Optional.of(u));

        mvc.perform(delete("/api/units/1")).andExpect(status().isNoContent());

        assertThat(u.isActive()).isFalse();
        verify(repository).save(u);
        verify(repository, never()).delete(any());
    }

    @Test
    void reservarUnaUnidadInactivaResponde409() throws Exception {
        Unit u = unit();
        u.setActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(u));

        mvc.perform(post("/api/units/1/reserve")).andExpect(status().isConflict());
    }

    @Test
    void liberarCupoDeUnaUnidadInactivaResponde200() throws Exception {
        Unit u = unit();
        u.reserveOne();
        u.setActive(false);
        when(repository.findById(1L)).thenReturn(Optional.of(u));
        when(repository.save(u)).thenReturn(u);

        mvc.perform(post("/api/units/1/release")).andExpect(status().isOk());
        assertThat(u.getAvailableSlots()).isEqualTo(3);
    }
}
