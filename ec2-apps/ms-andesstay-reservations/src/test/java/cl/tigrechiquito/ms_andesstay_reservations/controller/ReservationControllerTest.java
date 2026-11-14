package cl.tigrechiquito.ms_andesstay_reservations.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.repository.ReservationRepository;
import cl.tigrechiquito.ms_andesstay_reservations.service.ReservationService;

/**
 * Controller + service reales; repositorio y catalog simulados (los tests no
 * levantan Postgres ni catalog).
 */
@WebMvcTest(ReservationController.class)
@Import(ReservationService.class)
class ReservationControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ReservationRepository repository;

    @MockitoBean
    CatalogClient catalogClient;

    static Reservation reservation(long id, String guestId) {
        Reservation r = new Reservation(guestId, "Huésped " + guestId, 1L,
                LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12));
        ReflectionTestUtils.setField(r, "id", id);
        ReflectionTestUtils.setField(r, "createdAt", Instant.parse("2029-12-01T00:00:00Z"));
        ReflectionTestUtils.setField(r, "updatedAt", Instant.parse("2029-12-01T00:00:00Z"));
        return r;
    }

    // ---------- P0-1: visibilidad de reservas ----------

    @Test
    void clienteSoloVeSusReservasEnElListado() throws Exception {
        when(repository.findByGuestIdOrderByCreatedAtDesc("A")).thenReturn(List.of(reservation(1, "A")));
        when(repository.findAll()).thenReturn(List.of(reservation(1, "A"), reservation(2, "B")));

        mvc.perform(get("/api/reservations").header("X-User-Id", "A").header("X-User-Roles", "Cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].guestId").value("A"));
    }

    @Test
    void personalVeTodasLasReservasEnElListado() throws Exception {
        when(repository.findAll()).thenReturn(List.of(reservation(1, "A"), reservation(2, "B")));

        for (String role : List.of("Recepcionista", "Admin", "Auditor")) {
            mvc.perform(get("/api/reservations").header("X-User-Id", "staff").header("X-User-Roles", role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Test
    void clienteNoVeLaReservaDeOtroYRecibe404() throws Exception {
        when(repository.findById(2L)).thenReturn(Optional.of(reservation(2, "B")));

        mvc.perform(get("/api/reservations/2").header("X-User-Id", "A").header("X-User-Roles", "Cliente"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteVeSuPropiaReserva() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(reservation(1, "A")));

        mvc.perform(get("/api/reservations/1").header("X-User-Id", "A").header("X-User-Roles", "Cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestId").value("A"));
    }

    @Test
    void personalVeCualquierReservaPorId() throws Exception {
        when(repository.findById(2L)).thenReturn(Optional.of(reservation(2, "B")));

        for (String role : List.of("Recepcionista", "Admin", "Auditor")) {
            mvc.perform(get("/api/reservations/2").header("X-User-Id", "staff").header("X-User-Roles", role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.guestId").value("B"));
        }
    }

    @Test
    void mineDevuelveLasReservasDelUsuario() throws Exception {
        when(repository.findByGuestIdOrderByCreatedAtDesc("A"))
                .thenReturn(List.of(reservation(3, "A"), reservation(1, "A")));

        mvc.perform(get("/api/reservations/mine").header("X-User-Id", "A").header("X-User-Roles", "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(3));
    }

    @Test
    void mineSinUsuarioResponde401() throws Exception {
        mvc.perform(get("/api/reservations/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listadoSinUsuarioNiRolDePersonalResponde401() throws Exception {
        when(repository.findByGuestIdOrderByCreatedAtDesc(any())).thenReturn(List.of());

        mvc.perform(get("/api/reservations"))
                .andExpect(status().isUnauthorized());
    }
}
