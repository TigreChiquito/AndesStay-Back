package cl.tigrechiquito.ms_andesstay_rabbit_admin.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.client.ManagementApiModels.QueueJson;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.client.ManagementClient;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.config.AdminProperties;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.BindingView;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.ExchangeView;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.Node;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.Overview;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.AdminViews.QueueView;

/**
 * Listados y métricas del clúster a partir de la API HTTP de la Management UI.
 * Omite el exchange por defecto ("") y los amq.* que RabbitMQ crea solo, y los
 * bindings implícitos del exchange por defecto, para mostrar solo la topología real.
 */
@Service
public class MonitoringService {

    private final ManagementClient client;
    private final AdminProperties properties;

    public MonitoringService(ManagementClient client, AdminProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Overview overview() {
        List<QueueView> queues = queues();
        long pending = queues.stream().mapToLong(QueueView::messages).sum();
        int consumers = queues.stream().mapToInt(QueueView::consumers).sum();
        long dlqMessages = queues.stream().filter(QueueView::dlq).mapToLong(QueueView::messages).sum();
        List<Node> nodes = client.nodes().stream()
                .map(n -> new Node(n.name(), Boolean.TRUE.equals(n.running())))
                .toList();
        return new Overview(queues.size(), pending, consumers, dlqMessages, nodes,
                properties.management().publicUrl());
    }

    public List<QueueView> queues() {
        return client.queues().stream()
                .map(this::toView)
                .sorted(Comparator.comparing(QueueView::name))
                .toList();
    }

    public List<ExchangeView> exchanges() {
        return client.exchanges().stream()
                .filter(e -> !e.name().isEmpty() && !e.name().startsWith("amq."))
                .map(e -> new ExchangeView(e.name(), e.type(), Boolean.TRUE.equals(e.durable()),
                        properties.isProtected(e.name())))
                .sorted(Comparator.comparing(ExchangeView::name))
                .toList();
    }

    public List<BindingView> bindings() {
        return client.bindings().stream()
                .filter(b -> !b.source().isEmpty() && "queue".equals(b.destinationType()))
                .map(b -> new BindingView(b.source(), b.destination(), b.routingKey(),
                        properties.isProtected(b.destination())))
                .sorted(Comparator.comparing(BindingView::exchange).thenComparing(BindingView::queue))
                .toList();
    }

    private QueueView toView(QueueJson q) {
        Map<String, Object> args = q.arguments() != null ? q.arguments() : Map.of();
        return new QueueView(
                q.name(),
                Boolean.TRUE.equals(q.durable()),
                q.state(),
                q.node(),
                orZero(q.messages()),
                orZero(q.messagesReady()),
                orZero(q.messagesUnacknowledged()),
                q.consumers() != null ? q.consumers() : 0,
                q.name().endsWith(RabbitAdminService.DLQ_SUFFIX),
                asString(args.get("x-dead-letter-routing-key")),
                asLong(args.get("x-message-ttl")),
                asLong(args.get("x-max-length")),
                asString(args.get("x-overflow")),
                properties.isProtected(q.name()));
    }

    private static long orZero(Long value) {
        return value != null ? value : 0L;
    }

    private static Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private static String asString(Object value) {
        return value != null ? value.toString() : null;
    }
}
