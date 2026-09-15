package cl.tigrechiquito.ms_andesstay_notify.messaging;

import java.time.Instant;

/**
 * Copia del envelope común de la pauta, del lado consumidor. Mismos campos que
 * el de reservations para que Jackson mapee el JSON campo por campo.
 */
public record CommandEnvelope<T>(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        T payload
) {
}