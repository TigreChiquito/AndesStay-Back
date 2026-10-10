package cl.tigrechiquito.ms_andesstay_notify.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_notify.dto.NotificationResponse;
import cl.tigrechiquito.ms_andesstay_notify.service.NotificationService;

/**
 * Bandeja de avisos in-app. La identidad la propaga el BFF desde el token
 * validado: X-User-Id (oid) y X-User-Roles (App Roles separados por coma).
 * Un huésped ve sus avisos; Recepcionista/Admin ven además los del personal.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_ROLES = "X-User-Roles";

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    /** GET /api/notifications — últimos 50 avisos visibles, más recientes primero. */
    @GetMapping
    public List<NotificationResponse> list(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLES, required = false) String roles) {

        return service.visibleTo(userId, isStaff(roles)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /** PATCH /api/notifications/{id}/read — marca un aviso como leído (404 si no es visible). */
    @PatchMapping("/{id}/read")
    public NotificationResponse markRead(
            @PathVariable Long id,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLES, required = false) String roles) {

        return NotificationResponse.from(service.markRead(id, userId, isStaff(roles)));
    }

    /** PATCH /api/notifications/read-all — marca como leídos todos los avisos visibles. */
    @PatchMapping("/read-all")
    public Map<String, Integer> markAllRead(
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId,
            @RequestHeader(value = HEADER_USER_ROLES, required = false) String roles) {

        return Map.of("updated", service.markAllRead(userId, isStaff(roles)));
    }

    private static boolean isStaff(String roles) {
        if (roles == null) {
            return false;
        }
        return Arrays.stream(roles.split(","))
                .map(String::trim)
                .anyMatch(role -> role.equals("Recepcionista") || role.equals("Admin"));
    }
}
