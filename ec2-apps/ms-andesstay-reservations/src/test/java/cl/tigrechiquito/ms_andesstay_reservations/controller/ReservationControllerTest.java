package cl.tigrechiquito.ms_andesstay_reservations.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogClient;
import cl.tigrechiquito.ms_andesstay_reservations.client.CatalogUnit;
import cl.tigrechiquito.ms_andesstay_reservations.client.UnitNotFoundInCatalogException;
import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
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

    private void saveReturnsWithId() {
        when(catalogClient.getUnit(any())).thenReturn(new CatalogUnit(1L, true));
        when(repository.save(any(Reservation.class))).thenAnswer(inv -> {
            Reservation r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 99L);
            return r;
        });
    }

    static String createBody(String guestId) {
        return createBody(guestId, 1L, LocalDate.now().plusDays(10));
    }

    static String createBody(String guestId, long unitId, LocalDate in) {
        return """
                {"guestId":"%s","guestName":"Ana","unitId":%d,"checkInDate":"%s","checkOutDate":"%s"}
                """.formatted(guestId, unitId, in, in.plusDays(2));
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

    // ---------- P0-2: guestId de la reserva ----------

    @Test
    void clienteNoPuedeReservarANombreDeOtro() throws Exception {
        saveReturnsWithId();

        mvc.perform(post("/api/reservations").header("X-User-Id", "A").header("X-User-Roles", "Cliente")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("otro")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestId").value("A"));
    }

    @Test
    void recepcionistaReservaANombreDelHuesped() throws Exception {
        saveReturnsWithId();

        mvc.perform(post("/api/reservations").header("X-User-Id", "recep").header("X-User-Roles", "Recepcionista")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("huesped-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.guestId").value("huesped-1"));
    }

    @Test
    void crearSinUsuarioResponde401() throws Exception {
        mvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("otro")))
                .andExpect(status().isUnauthorized());
    }

    // ---------- P1-1: choque optimista ----------

    @Test
    void choqueDeVersionResponde409() throws Exception {
        Reservation r = reservation(1, "A");
        ReflectionTestUtils.setField(r, "status", ReservationStatus.EN_ESTADIA);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(r));
        when(repository.saveAndFlush(any(Reservation.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Reservation.class, 1L));

        mvc.perform(put("/api/reservations/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CHECKOUT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La reserva cambió mientras la editabas. Recarga e intenta de nuevo."));
    }

    // ---------- P1-3: validaciones al crear ----------

    @Test
    void crearConLlegadaEnElPasadoResponde400() throws Exception {
        saveReturnsWithId();

        mvc.perform(post("/api/reservations").header("X-User-Id", "A").header("X-User-Roles", "Cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("A", 1L, LocalDate.now().minusDays(3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("La fecha de llegada no puede ser anterior a hoy."));
    }

    @Test
    void crearSobreUnidadInexistenteResponde400() throws Exception {
        when(catalogClient.getUnit(9999L)).thenThrow(new UnitNotFoundInCatalogException(9999L));

        mvc.perform(post("/api/reservations").header("X-User-Id", "A").header("X-User-Roles", "Cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("A", 9999L, LocalDate.now().plusDays(3))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearSobreUnidadInactivaResponde400() throws Exception {
        when(catalogClient.getUnit(5L)).thenReturn(new CatalogUnit(5L, false));

        mvc.perform(post("/api/reservations").header("X-User-Id", "A").header("X-User-Roles", "Cliente")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("A", 5L, LocalDate.now().plusDays(3))))
                .andExpect(status().isBadRequest());
    }

    // ---------- P2-1: campos de la máquina de estados en el JSON ----------

    @Test
    void elJsonIncluyeTransicionesSiguientePasoYCancelable() throws Exception {
        Reservation r = reservation(1, "A");
        ReflectionTestUtils.setField(r, "status", ReservationStatus.CHECKIN_PENDIENTE);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        mvc.perform(get("/api/reservations/1").header("X-User-Id", "A").header("X-User-Roles", "Cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions[0]").value("EN_ESTADIA"))
                .andExpect(jsonPath("$.allowedTransitions[1]").value("CANCELADA"))
                .andExpect(jsonPath("$.nextStatus").value("EN_ESTADIA"))
                .andExpect(jsonPath("$.cancellable").value(true));
    }

    @Test
    void unaReservaTerminalTieneNextStatusNulo() throws Exception {
        Reservation r = reservation(1, "A");
        ReflectionTestUtils.setField(r, "status", ReservationStatus.CHECKOUT);
        when(repository.findById(1L)).thenReturn(Optional.of(r));

        mvc.perform(get("/api/reservations/1").header("X-User-Id", "A").header("X-User-Roles", "Cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions").isEmpty())
                .andExpect(jsonPath("$.nextStatus").isEmpty())
                .andExpect(jsonPath("$.cancellable").value(false));
    }
}
