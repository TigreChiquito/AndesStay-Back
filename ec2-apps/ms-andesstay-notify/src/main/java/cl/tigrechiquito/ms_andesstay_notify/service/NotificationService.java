package cl.tigrechiquito.ms_andesstay_notify.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cl.tigrechiquito.ms_andesstay_notify.messaging.CommandEnvelope;
import cl.tigrechiquito.ms_andesstay_notify.messaging.NotificationPayload;

/**
 * Ejecuta la acción de cada comando. Para el caso se simula con logs (no hay un
 * servidor SMTP ni generación real de PDF). El punto es demostrar que el mensaje
 * llegó, se deserializó y se procesó.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendEmail(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        log.info("EMAIL [{}] -> huésped {} (reserva {}): estadía {} a {}",
                env.type(), p.guestName(), p.reservationId(), p.checkInDate(), p.checkOutDate());
    }

    public void generateVoucher(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        log.info("VOUCHER [{}] -> generando PDF de la reserva {} (unidad {})",
                env.type(), p.reservationId(), p.unitId());
    }

    public void createHousekeepingTicket(CommandEnvelope<NotificationPayload> env) {
        NotificationPayload p = env.payload();
        log.info("HOUSEKEEPING [{}] -> ticket para preparar unidad {} (reserva {}, check-in {})",
                env.type(), p.unitId(), p.reservationId(), p.checkInDate());
    }
}