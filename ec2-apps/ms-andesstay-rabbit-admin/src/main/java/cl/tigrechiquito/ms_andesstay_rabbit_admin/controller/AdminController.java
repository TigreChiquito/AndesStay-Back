package cl.tigrechiquito.ms_andesstay_rabbit_admin.controller;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.BindingView;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.ExchangeView;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.Overview;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.QueueInfo;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.QueueView;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.BindingRequest;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.CreateExchangeRequest;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.CreateQueueRequest;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.service.MonitoringService;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.service.RabbitAdminService;

import jakarta.validation.Valid;

/**
 * API del administrador de RabbitMQ. A través del BFF, solo el rol Admin.
 *
 *   Escritura (RabbitAdmin, AMQP):  crear/eliminar colas, exchanges y bindings
 *   Lectura (API de la Management UI): métricas y listados de la topología
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RabbitAdminService admin;
    private final MonitoringService monitoring;

    public AdminController(RabbitAdminService admin, MonitoringService monitoring) {
        this.admin = admin;
        this.monitoring = monitoring;
    }

    // --- Lectura ---

    /** GET /api/admin/overview — métricas del clúster, nodos y URL de la Management UI. */
    @GetMapping("/overview")
    public Overview overview() {
        return monitoring.overview();
    }

    /** GET /api/admin/queues — todas las colas con métricas, DLQ y retención. */
    @GetMapping("/queues")
    public List<QueueView> queues() {
        return monitoring.queues();
    }

    /** GET /api/admin/queues/{name} — mensajes pendientes y consumidores de una cola. */
    @GetMapping("/queues/{name}")
    public QueueInfo queue(@PathVariable String name) {
        return admin.queueInfo(name);
    }

    /** GET /api/admin/exchanges — exchanges de la topología (sin los amq.* ni el por defecto). */
    @GetMapping("/exchanges")
    public List<ExchangeView> exchanges() {
        return monitoring.exchanges();
    }

    /** GET /api/admin/bindings — bindings exchange -> cola. */
    @GetMapping("/bindings")
    public List<BindingView> bindings() {
        return monitoring.bindings();
    }

    // --- Escritura ---

    /** POST /api/admin/queues — crea una cola (opcional: DLQ y retención). */
    @PostMapping("/queues")
    public ResponseEntity<QueueInfo> createQueue(@Valid @RequestBody CreateQueueRequest request) {
        QueueInfo created = admin.createQueue(request);
        return ResponseEntity.created(URI.create("/api/admin/queues/" + created.name())).body(created);
    }

    /** DELETE /api/admin/queues/{name} — elimina una cola. */
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Void> deleteQueue(@PathVariable String name) {
        admin.deleteQueue(name);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/admin/exchanges — crea un exchange direct, topic o fanout. */
    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, String>> createExchange(@Valid @RequestBody CreateExchangeRequest request) {
        admin.createExchange(request);
        return ResponseEntity.created(URI.create("/api/admin/exchanges/" + request.name()))
                .body(Map.of("name", request.name(), "type", request.type()));
    }

    /** DELETE /api/admin/exchanges/{name} — elimina un exchange. */
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Void> deleteExchange(@PathVariable String name) {
        admin.deleteExchange(name);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/admin/bindings — une un exchange con una cola. */
    @PostMapping("/bindings")
    public ResponseEntity<BindingRequest> createBinding(@Valid @RequestBody BindingRequest request) {
        admin.createBinding(request);
        return ResponseEntity.status(201).body(request);
    }

    /** DELETE /api/admin/bindings — quita un binding (mismo body que al crearlo). */
    @DeleteMapping("/bindings")
    public ResponseEntity<Void> deleteBinding(@Valid @RequestBody BindingRequest request) {
        admin.deleteBinding(request);
        return ResponseEntity.noContent().build();
    }
}
