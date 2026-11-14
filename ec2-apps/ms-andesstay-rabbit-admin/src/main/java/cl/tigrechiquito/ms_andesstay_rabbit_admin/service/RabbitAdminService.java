package cl.tigrechiquito.ms_andesstay_rabbit_admin.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Binding.DestinationType;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.ShutdownSignalException;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.config.AdminProperties;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ResourceNotFoundException;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.QueueInfo;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.BindingRequest;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.CreateExchangeRequest;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.CreateQueueRequest;

/**
 * Crea y elimina la topología del clúster por AMQP con RabbitAdmin.
 *
 * Reglas:
 *  - Los nombres de la topología del sistema (cmd.*, q.cmd.*) y los reservados
 *    por RabbitMQ (amq.*) no se pueden crear ni eliminar -> 409.
 *  - Crear algo que ya existe -> 409; eliminar algo que no existe -> 404.
 *  - Un binding solo se crea si el exchange y la cola existen -> si no, 400.
 */
@Service
public class RabbitAdminService {

    /** DLX compartido por todas las colas (el mismo que usan las del sistema). */
    static final String DLX = "cmd.dead.dlx";
    static final String DLQ_SUFFIX = ".dlq";

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;
    private final AdminProperties properties;

    public RabbitAdminService(RabbitAdmin rabbitAdmin, AdminProperties properties) {
        this.rabbitAdmin = rabbitAdmin;
        this.properties = properties;
    }

    // --- Colas ---

    /** Crea la cola (y su DLQ si se pide) con la política de retención indicada. */
    public QueueInfo createQueue(CreateQueueRequest req) {
        String name = req.name();
        requireNotProtected(name, "la cola");
        if (queueExists(name)) {
            throw new ResourceConflictException("La cola '" + name + "' ya existe");
        }

        Map<String, Object> args = new HashMap<>();
        if (req.isWithDlq()) {
            String dlqName = name + DLQ_SUFFIX;
            if (queueExists(dlqName)) {
                throw new ResourceConflictException("La cola '" + dlqName + "' ya existe");
            }
            rabbitAdmin.declareExchange(new DirectExchange(DLX, true, false));
            rabbitAdmin.declareQueue(new Queue(dlqName, req.isDurable(), false, false));
            rabbitAdmin.declareBinding(new Binding(dlqName, DestinationType.QUEUE, DLX, dlqName, null));
            args.put("x-dead-letter-exchange", DLX);
            args.put("x-dead-letter-routing-key", dlqName);
        }
        if (req.messageTtlMs() != null) {
            args.put("x-message-ttl", req.messageTtlMs());
        }
        if (req.maxLength() != null) {
            args.put("x-max-length", req.maxLength());
        }
        if (req.overflow() != null) {
            args.put("x-overflow", req.overflow());
        }

        rabbitAdmin.declareQueue(new Queue(name, req.isDurable(), false, false, args));
        log.info("ADMIN cola creada: {} (dlq={}, args={})", name, req.isWithDlq(), args);
        return queueInfo(name);
    }

    /** Elimina la cola y, si se creó con withDlq, también su "&lt;name&gt;.dlq". */
    public void deleteQueue(String name) {
        requireNotProtected(name, "la cola");
        if (!queueExists(name)) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        rabbitAdmin.deleteQueue(name);
        log.info("ADMIN cola eliminada: {}", name);

        String dlqName = name + DLQ_SUFFIX;
        if (queueExists(dlqName)) {
            rabbitAdmin.deleteQueue(dlqName);
            log.info("ADMIN DLQ eliminada junto a su cola: {}", dlqName);
        }
    }

    /** Mensajes pendientes y consumidores de una cola (por AMQP). */
    public QueueInfo queueInfo(String name) {
        QueueInformation info = rabbitAdmin.getQueueInfo(name);
        if (info == null) {
            throw new ResourceNotFoundException("La cola '" + name + "' no existe");
        }
        return new QueueInfo(info.getName(), info.getMessageCount(), info.getConsumerCount());
    }

    // --- Exchanges ---

    public void createExchange(CreateExchangeRequest req) {
        String name = req.name();
        requireNotProtected(name, "el exchange");
        if (exchangeExists(name)) {
            throw new ResourceConflictException("El exchange '" + name + "' ya existe");
        }
        Exchange exchange = switch (req.type()) {
            case "direct" -> new DirectExchange(name, req.isDurable(), false);
            case "topic" -> new TopicExchange(name, req.isDurable(), false);
            case "fanout" -> new FanoutExchange(name, req.isDurable(), false);
            default -> throw new IllegalArgumentException("type debe ser direct, topic o fanout");
        };
        rabbitAdmin.declareExchange(exchange);
        log.info("ADMIN exchange creado: {} ({})", name, req.type());
    }

    public void deleteExchange(String name) {
        requireNotProtected(name, "el exchange");
        if (!exchangeExists(name)) {
            throw new ResourceNotFoundException("El exchange '" + name + "' no existe");
        }
        rabbitAdmin.deleteExchange(name);
        log.info("ADMIN exchange eliminado: {}", name);
    }

    // --- Bindings ---

    /**
     * Une un exchange con una cola. Se permite usar un exchange del sistema
     * (ej. enganchar una cola propia a cmd.topic para observar el tráfico),
     * pero no tocar los bindings de las colas del sistema.
     */
    public void createBinding(BindingRequest req) {
        requireNotProtected(req.queue(), "la cola");
        requireBindingEnds(req);
        rabbitAdmin.declareBinding(toBinding(req));
        log.info("ADMIN binding creado: {} -[{}]-> {}", req.exchange(), req.routingKey(), req.queue());
    }

    public void deleteBinding(BindingRequest req) {
        requireNotProtected(req.queue(), "la cola");
        requireBindingEnds(req);
        rabbitAdmin.removeBinding(toBinding(req));
        log.info("ADMIN binding eliminado: {} -[{}]-> {}", req.exchange(), req.routingKey(), req.queue());
    }

    // --- Helpers ---

    private void requireBindingEnds(BindingRequest req) {
        if (!exchangeExists(req.exchange())) {
            throw new IllegalArgumentException("El exchange '" + req.exchange() + "' no existe");
        }
        if (!queueExists(req.queue())) {
            throw new IllegalArgumentException("La cola '" + req.queue() + "' no existe");
        }
    }

    private static Binding toBinding(BindingRequest req) {
        return new Binding(req.queue(), DestinationType.QUEUE, req.exchange(), req.routingKey(), null);
    }

    private void requireNotProtected(String name, String what) {
        if (properties.isProtected(name)) {
            throw new ResourceConflictException("No se puede modificar " + what + " '" + name
                    + "': es un recurso protegido (" + String.join(", ", properties.protectedPrefixes()) + ")");
        }
    }

    boolean queueExists(String name) {
        return rabbitAdmin.getQueueInfo(name) != null;
    }

    /**
     * RabbitAdmin no tiene "existe exchange": se usa una declaración pasiva. Solo
     * el 404 del broker significa "no existe"; otro error (broker caído) se propaga.
     */
    boolean exchangeExists(String name) {
        try {
            return Boolean.TRUE.equals(rabbitAdmin.getRabbitTemplate().execute(channel -> {
                channel.exchangeDeclarePassive(name);
                return true;
            }));
        } catch (AmqpException ex) {
            if (isNotFound(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private static boolean isNotFound(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ShutdownSignalException sse
                    && sse.getReason() instanceof AMQP.Channel.Close close
                    && close.getReplyCode() == AMQP.NOT_FOUND) {
                return true;
            }
        }
        return false;
    }
}
