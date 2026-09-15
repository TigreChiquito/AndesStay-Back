package cl.tigrechiquito.ms_andesstay_reservations.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_reservations.domain.Reservation;
import cl.tigrechiquito.ms_andesstay_reservations.domain.ReservationStatus;
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

    /** POST /api/reservations — crea una reserva (nace en CREADA). */
    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @Valid @RequestBody CreateReservationRequest request) {

        Reservation created = service.create(request);
        URI location = URI.create("/api/reservations/" + created.getId());
        return ResponseEntity.created(location).body(ReservationResponse.from(created));
    }

    /** GET /api/reservations/{id} */
    @GetMapping("/{id}")
    public ReservationResponse getById(@PathVariable Long id) {
        return ReservationResponse.from(service.getById(id));
    }

    /** PUT /api/reservations/{id}/status — cambia el estado según la máquina. */
    @PutMapping("/{id}/status")
    public ReservationResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {

        return ReservationResponse.from(service.changeStatus(id, request.status()));
    }

    /** GET /api/reservations?status=...&from=...&to=... — todos los filtros opcionales. */
    @GetMapping
    public List<ReservationResponse> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        ReservationStatus parsedStatus = (status == null || status.isBlank())
                ? null
                : ReservationStatus.fromJson(status); // mismo parseo tolerante que el body

        return service.search(parsedStatus, from, to).stream()
                .map(ReservationResponse::from)
                .toList();
    }
}