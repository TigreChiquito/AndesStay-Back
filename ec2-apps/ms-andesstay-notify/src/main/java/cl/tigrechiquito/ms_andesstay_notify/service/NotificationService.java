package cl.tigrechiquito.ms_andesstay_notify.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.tigrechiquito.ms_andesstay_notify.domain.Audience;
import cl.tigrechiquito.ms_andesstay_notify.domain.Notification;
import cl.tigrechiquito.ms_andesstay_notify.domain.NotificationNotFoundException;
import cl.tigrechiquito.ms_andesstay_notify.messaging.CommandEnvelope;
import cl.tigrechiquito.ms_andesstay_notify.messaging.NotificationPayload;
import cl.tigrechiquito.ms_andesstay_notify.repository.NotificationRepository;

/**
 * Convierte los comandos de RabbitMQ en avisos in-app y los expone a la API.
 *
 *   q.cmd.notification -> aviso al huésped (GUEST, recipientId = guestId)
 *   q.cmd.housekeeping -> aviso al personal (STAFF: Recepcionista/Admin)
 *   q.cmd.voucher      -> generación del voucher (pendiente: solo log)
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final int MAX_RESULTS = 50;

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    // --- Comandos (RabbitMQ) ---

    @Transactional
    public void notifyGuest(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        if (p.guestId() == null || p.guestId().isBlank()) {
            log.warn("Aviso {} de la reserva {} sin guestId: se descarta", env.type(), p.reservationId());
            return;
        }
        String stay = "del " + fmt(p.checkInDate()) + " al " + fmt(p.checkOutDate());

        String[] text = switch (env.type()) {
            case "notification.created" -> new String[] {
                    "Recibimos tu reserva",
                    "Tu reserva #" + p.reservationId() + " en la unidad " + p.unitId() + " " + stay
                            + " quedó registrada y está pendiente de confirmación." };
            case "notification.confirmed" -> new String[] {
                    "Reserva confirmada",
                    "Tu reserva #" + p.reservationId() + " en la unidad " + p.unitId() + " " + stay
                            + " fue confirmada. ¡Te esperamos!" };
            case "notification.checkout" -> new String[] {
                    "Gracias por tu estadía",
                    "Registramos el checkout de tu reserva #" + p.reservationId()
                            + ". ¡Esperamos verte pronto!" };
            case "notification.cancelled" -> new String[] {
                    "Reserva cancelada",
                    "Tu reserva #" + p.reservationId() + " en la unidad " + p.unitId() + " " + stay
                            + " fue cancelada." };
            default -> new String[] {
                    "Actualización de tu reserva",
                    "Tu reserva #" + p.reservationId() + " está en estado " + p.status() + "." };
        };
        save(env, Audience.GUEST, p.guestId(), text[0], text[1]);
    }

    @Transactional
    public void notifyStaff(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        String guest = p.guestName() != null ? p.guestName() : "Huésped " + p.guestId();

        String[] text = switch (env.type()) {
            case "housekeeping.new_reservation" -> new String[] {
                    "Nueva reserva por confirmar",
                    guest + " reservó la unidad " + p.unitId() + " del " + fmt(p.checkInDate())
                            + " al " + fmt(p.checkOutDate()) + " (reserva #" + p.reservationId() + ")." };
            case "housekeeping.prepare_unit" -> new String[] {
                    "Preparar unidad " + p.unitId(),
                    "Check-in de " + guest + " el " + fmt(p.checkInDate())
                            + " (reserva #" + p.reservationId() + ")." };
            case "housekeeping.clean_unit" -> new String[] {
                    "Limpiar unidad " + p.unitId(),
                    guest + " hizo checkout (reserva #" + p.reservationId() + ")." };
            case "housekeeping.reservation_cancelled" -> new String[] {
                    "Reserva cancelada",
                    "La reserva #" + p.reservationId() + " de " + guest + " en la unidad "
                            + p.unitId() + " fue cancelada." };
            default -> new String[] {
                    "Aviso de reserva #" + p.reservationId(),
                    "La reserva #" + p.reservationId() + " está en estado " + p.status() + "." };
        };
        save(env, Audience.STAFF, null, text[0], text[1]);
    }

    public void generateVoucher(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        log.info("VOUCHER [{}] -> generando PDF de la reserva {} (unidad {})",
                env.type(), p.reservationId(), p.unitId());
    }

    // --- API ---

    @Transactional(readOnly = true)
    public List<Notification> visibleTo(String userId, boolean staff) {
        return repository.findVisibleTo(userId, staff, PageRequest.of(0, MAX_RESULTS));
    }

    @Transactional
    public Notification markRead(Long id, String userId, boolean staff) {
        Notification n = repository.findById(id)
                .filter(found -> found.isVisibleTo(userId, staff))
                .orElseThrow(() -> new NotificationNotFoundException(id));
        n.markRead();
        return n;
    }

    @Transactional
    public int markAllRead(String userId, boolean staff) {
        return repository.markAllReadFor(userId, staff);
    }

    // --- Helpers ---

    /** Persiste el aviso. El eventId único evita duplicados si RabbitMQ reentrega. */
    private void save(CommandEnvelope<NotificationPayload> env, Audience audience,
                      String recipientId, String title, String message) {
        if (repository.existsByEventId(env.eventId())) {
            log.info("Aviso ya registrado (eventId={}), ignorado", env.eventId());
            return;
        }
        NotificationPayload p = env.payload();
        repository.save(new Notification(env.eventId(), audience, recipientId, env.type(),
                title, message, p.reservationId(), p.unitId()));
        log.info("AVISO {} [{}] reserva {}: {}", audience, env.type(), p.reservationId(), title);
    }

    private static String fmt(LocalDate date) {
        return date != null ? date.format(DATE) : "?";
    }
}
