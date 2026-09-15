package cl.tigrechiquito.ms_andesstay_catalog.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_catalog.domain.Unit;
import cl.tigrechiquito.ms_andesstay_catalog.domain.UnitType;
import cl.tigrechiquito.ms_andesstay_catalog.dto.CreateUnitRequest;
import cl.tigrechiquito.ms_andesstay_catalog.dto.UnitResponse;
import cl.tigrechiquito.ms_andesstay_catalog.dto.UpdateUnitRequest;
import cl.tigrechiquito.ms_andesstay_catalog.service.CatalogService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/units")
public class UnitController {

    private final CatalogService service;

    public UnitController(CatalogService service) {
        this.service = service;
    }

    /** POST /api/units — crea una unidad (nace activa y con todos los cupos libres). */
    @PostMapping
    public ResponseEntity<UnitResponse> create(@Valid @RequestBody CreateUnitRequest request) {
        Unit created = service.create(request);
        URI location = URI.create("/api/units/" + created.getId());
        return ResponseEntity.created(location).body(UnitResponse.from(created));
    }

    /** GET /api/units/{id} */
    @GetMapping("/{id}")
    public UnitResponse getById(@PathVariable Long id) {
        return UnitResponse.from(service.getById(id));
    }

    /** GET /api/units?type=...&available=true — filtros opcionales. */
    @GetMapping
    public List<UnitResponse> search(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "false") boolean available) {

        UnitType parsedType = (type == null || type.isBlank())
                ? null
                : UnitType.fromJson(type);

        return service.search(parsedType, available).stream()
                .map(UnitResponse::from)
                .toList();
    }

    /** PUT /api/units/{id} — actualiza datos, tarifa, estado y cupo total. */
    @PutMapping("/{id}")
    public UnitResponse update(@PathVariable Long id,
                               @Valid @RequestBody UpdateUnitRequest request) {
        return UnitResponse.from(service.update(id, request));
    }

    /** DELETE /api/units/{id} — elimina la unidad. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/units/{id}/reserve — descuenta un cupo (lo usa reservations al CONFIRMAR). */
    @PostMapping("/{id}/reserve")
    public UnitResponse reserve(@PathVariable Long id) {
        return UnitResponse.from(service.reserveOne(id));
    }

    /** POST /api/units/{id}/release — devuelve un cupo (lo usa reservations al CANCELAR). */
    @PostMapping("/{id}/release")
    public UnitResponse release(@PathVariable Long id) {
        return UnitResponse.from(service.releaseOne(id));
    }
}