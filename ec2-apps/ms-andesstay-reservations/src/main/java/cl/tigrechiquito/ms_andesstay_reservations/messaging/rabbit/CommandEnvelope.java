package cl.tigrechiquito.ms_andesstay_reservations.messaging.rabbit;

import java.time.Instant;
import java.util.UUID;

/**
 * Envelope común de la pauta (sección 8): metadatos estándar + payload.
 *
 * @param type          tipo de comando/evento (ej. "email.confirmation")
 * @param eventId       id único del mensaje; sirve al consumidor para idempotencia
 * @param timestamp     momento de emisión
 * @param traceId       correlación de la traza distribuida
 * @param correlationId correlación de negocio (ej. "reservation-42")
 * @param payload       dato específico del comando
 */
public record CommandEnvelope<T>(
        String type,
        String eventId,
        Instant timestamp,
        String traceId,
        String correlationId,
        T payload
) {

    /** Crea un envelope generando eventId y timestamp automáticamente. */
    public static <T> CommandEnvelope<T> create(String type, String correlationId, T payload) {
        return new CommandEnvelope<>(
                type,
                UUID.randomUUID().toString(),
                Instant.now(),
                UUID.randomUUID().toString(), // traceId: placeholder hasta integrar tracing real
                correlationId,
                payload);
    }
}