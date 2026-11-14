package cl.tigrechiquito.ms_andesstay_reservations.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
import cl.tigrechiquito.ms_andesstay_reservations.domain.UnidentifiedUserException;
import cl.tigrechiquito.ms_andesstay_reservations.dto.CreateReservationRequest;
import cl.tigrechiquito.ms_andesstay_reservations.dto.ReservationResponse;
import cl.tigrechiquito.ms_andesstay_reservations.dto.UpdateStatusRequest;
import cl.tigrechiquito.ms_andesstay_reservations.service.ReservationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    /**
     * POST /api/reservations — crea una reserva (nace en CREADA). El personal
     * puede indicar el guestId del huésped; para el resto se usa X-User-Id y el
     * guestId del body se ignora.
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        String guestId = isStaff(roles) ? request.guestId() : requireUser(userId);
        if (guestId == null || guestId.isBlank()) {
            throw new IllegalArgumentException("Indica el huésped de la reserva (guestId).");
        }
        Reservation created = service.create(request, guestId);
        URI location = URI.create("/api/reservations/" + created.getId());
        return ResponseEntity.created(location).body(ReservationResponse.from(created));
    }

    /**
     * GET /api/reservations/mine — las reservas del usuario autenticado (X-User-Id),
     * de la más reciente a la más antigua.
     */
    @GetMapping("/mine")
    public List<ReservationResponse> mine(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        return service.findByGuest(requireUser(userId)).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    /** GET /api/reservations/{id} — el personal ve cualquiera; el huésped solo las suyas (si no, 404). */
    @GetMapping("/{id}")
    public ReservationResponse getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        return ReservationResponse.from(service.getVisibleById(id, userId, canReadAll(roles)));
    }

    /** PUT /api/reservations/{id}/status — cambia el estado según la máquina. */
    @PutMapping("/{id}/status")
    public ReservationResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {

        return ReservationResponse.from(service.changeStatus(id, request.status()));
    }

    /**
     * POST /api/reservations/{id}/cancel — cancela la reserva. Un huésped solo puede
     * cancelar las suyas; el personal, cualquiera. La identidad la propaga el BFF
     * desde el token validado (X-User-Id = oid, X-User-Roles = App Roles).
     */
    @PostMapping("/{id}/cancel")
    public ReservationResponse cancel(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        return ReservationResponse.from(service.cancel(id, userId, isStaff(roles)));
    }

    /**
     * GET /api/reservations?status=...&from=...&to=... — todos los filtros opcionales.
     * El personal (y Auditor) ve todas; el resto, solo las suyas.
     */
    @GetMapping
    public List<ReservationResponse> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Roles", required = false) String roles) {

        String guestFilter = canReadAll(roles) ? null : requireUser(userId);

        ReservationStatus parsedStatus = (status == null || status.isBlank())
                ? null
                : ReservationStatus.fromJson(status); // mismo parseo tolerante que el body

        return service.search(parsedStatus, from, to, guestFilter).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    /** Personal con permisos de escritura: Recepcionista y Admin. */
    private static boolean isStaff(String roles) {
        return hasAnyRole(roles, "Recepcionista", "Admin");
    }

    /** Para lecturas también cuenta el Auditor. */
    private static boolean canReadAll(String roles) {
        return hasAnyRole(roles, "Recepcionista", "Admin", "Auditor");
    }

    private static boolean hasAnyRole(String roles, String... wanted) {
        if (roles == null) {
            return false;
        }
        List<String> wantedRoles = List.of(wanted);
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(wantedRoles::contains);
    }

    private static String requireUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnidentifiedUserException();
        }
        return userId;
    }
}
