package cl.tigrechiquito.ms_andesstay_notify.listener;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

import cl.tigrechiquito.ms_andesstay_notify.config.RabbitConstants;
import cl.tigrechiquito.ms_andesstay_notify.messaging.CommandEnvelope;
import cl.tigrechiquito.ms_andesstay_notify.messaging.NotificationPayload;
import cl.tigrechiquito.ms_andesstay_notify.messaging.ProcessedEventStore;
import cl.tigrechiquito.ms_andesstay_notify.service.NotificationService;

/**
 * Consume las 3 colas de comandos. Cada método:
 *  1. revisa idempotencia por eventId (ignora reentregas),
 *  2. procesa,
 *  3. hace ACK explícito si todo va bien,
 *  4. o NACK sin requeue si falla -> la cola tiene DLX, así que va a su DLQ.
 *
 * El modo de ACK manual se configura en application.yml
 * (spring.rabbitmq.listener.simple.acknowledge-mode: manual).
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService service;
    private final ProcessedEventStore processed;

    public NotificationListener(NotificationService service, ProcessedEventStore processed) {
        this.service = service;
        this.processed = processed;
    }

    @RabbitListener(queues = RabbitConstants.QUEUE_EMAIL)
    public void onEmail(CommandEnvelope<NotificationPayload> env, Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(env, channel, tag, () -> service.sendEmail(env));
    }

    @RabbitListener(queues = RabbitConstants.QUEUE_HOUSEKEEPING)
    public void onHousekeeping(CommandEnvelope<NotificationPayload> env, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(env, channel, tag, () -> service.createHousekeepingTicket(env));
    }

    @RabbitListener(queues = RabbitConstants.QUEUE_VOUCHER)
    public void onVoucher(CommandEnvelope<NotificationPayload> env, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        handle(env, channel, tag, () -> service.generateVoucher(env));
    }

    private void handle(CommandEnvelope<NotificationPayload> env, Channel channel, long tag,
                        Runnable work) throws IOException {
        if (processed.isProcessed(env.eventId())) {
            log.info("Mensaje duplicado (eventId={}) ignorado", env.eventId());
            channel.basicAck(tag, false);
            return;
        }
        try {
            work.run();
            processed.markProcessed(env.eventId());
            channel.basicAck(tag, false);            // ACK: procesado OK
        } catch (Exception ex) {
            log.error("Fallo procesando eventId={}, se envía a DLQ", env.eventId(), ex);
            channel.basicNack(tag, false, false);    // NACK sin requeue -> DLX -> DLQ
        }
    }
}