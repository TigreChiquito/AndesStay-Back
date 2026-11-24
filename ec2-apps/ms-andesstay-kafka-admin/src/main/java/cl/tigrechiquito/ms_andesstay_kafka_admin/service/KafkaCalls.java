package cl.tigrechiquito.ms_andesstay_kafka_admin.service;

import java.util.concurrent.ExecutionException;

import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.errors.ApiException;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.KafkaUnavailableException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceNotFoundException;

/**
 * Espera el resultado de una llamada del AdminClient y traduce los errores de
 * Kafka a los del dominio (que GlobalExceptionHandler convierte en HTTP).
 */
final class KafkaCalls {

    private KafkaCalls() {
    }

    /** @param what sujeto para el mensaje de error, ej. "El tópico pagos". */
    static <T> T await(KafkaFuture<T> future, String what) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaUnavailableException("Operación interrumpida");
        } catch (ExecutionException e) {
            throw translate(e.getCause(), what);
        }
    }

    static RuntimeException translate(Throwable cause, String what) {
        if (cause instanceof TopicExistsException) {
            return new ResourceConflictException(what + " ya existe.");
        }
        if (cause instanceof UnknownTopicOrPartitionException || cause instanceof GroupIdNotFoundException) {
            return new ResourceNotFoundException(what + " no existe.");
        }
        if (cause instanceof GroupNotEmptyException) {
            return new ResourceConflictException(what + " tiene consumidores activos.");
        }
        // Timeouts, broker caído, líder en elección...: reintentar más tarde
        if (cause instanceof RetriableException) {
            return new KafkaUnavailableException("Kafka no responde. Intenta de nuevo en unos minutos.");
        }
        // El resto de errores de la API son de la petición (particiones, réplicas, config inválida...)
        if (cause instanceof ApiException) {
            return new IllegalArgumentException(cause.getMessage());
        }
        return new KafkaUnavailableException("Error inesperado de Kafka.");
    }
}
