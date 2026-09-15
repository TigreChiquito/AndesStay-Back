package cl.tigrechiquito.ms_andesstay_notify.messaging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Registro de eventos ya procesados, para idempotencia: si un mensaje se
 * reentrega (redelivery), no se vuelve a procesar.
 *
 * Es en memoria (Set concurrente), así que se pierde al reiniciar y es por
 * instancia. Suficiente para el caso; en producción esto iría en Redis o una
 * tabla, para que sea compartido y persistente.
 */
@Component
public class ProcessedEventStore {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    public boolean isProcessed(String eventId) {
        return eventId != null && processed.contains(eventId);
    }

    public void markProcessed(String eventId) {
        if (eventId != null) {
            processed.add(eventId);
        }
    }
}