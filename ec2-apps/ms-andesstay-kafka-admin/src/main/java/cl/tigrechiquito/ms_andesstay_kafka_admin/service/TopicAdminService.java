package cl.tigrechiquito.ms_andesstay_kafka_admin.service;

import static cl.tigrechiquito.ms_andesstay_kafka_admin.service.KafkaCalls.await;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.CreateTopic;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.ConfigValue;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.PartitionInfo;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.TopicDetail;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.TopicSummary;

/**
 * Tópicos: listar, describir, crear, ajustar configuración, aumentar particiones
 * y eliminar. Los tópicos del sistema (prefijos protegidos) no se eliminan ni se
 * reparticionan; su configuración sí se puede ajustar (salvo los internos "_").
 */
@Service
public class TopicAdminService {

    private static final Logger log = LoggerFactory.getLogger(TopicAdminService.class);

    /** Lo único que se puede tocar desde el administrador (el resto queda al broker). */
    static final List<String> EDITABLE_CONFIGS = List.of(
            TopicConfig.CLEANUP_POLICY_CONFIG,
            TopicConfig.RETENTION_MS_CONFIG,
            TopicConfig.RETENTION_BYTES_CONFIG,
            TopicConfig.SEGMENT_MS_CONFIG,
            TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG,
            TopicConfig.MAX_MESSAGE_BYTES_CONFIG,
            TopicConfig.COMPRESSION_TYPE_CONFIG);

    private final Admin admin;
    private final List<String> protectedPrefixes;

    public TopicAdminService(Admin admin, @Value("${kafka-admin.protected-prefixes}") List<String> protectedPrefixes) {
        this.admin = admin;
        this.protectedPrefixes = protectedPrefixes;
    }

    public boolean isProtected(String name) {
        return protectedPrefixes.stream().anyMatch(name::startsWith);
    }

    public List<TopicSummary> list() {
        Set<String> names = await(admin.listTopics(new ListTopicsOptions().listInternal(true)).names(), "Los tópicos");
        return await(admin.describeTopics(names).allTopicNames(), "Los tópicos").values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(t -> new TopicSummary(t.name(), t.partitions().size(),
                        t.partitions().isEmpty() ? 0 : t.partitions().get(0).replicas().size(),
                        t.isInternal(), isProtected(t.name())))
                .toList();
    }

    public TopicDetail get(String name) {
        TopicDescription t = await(admin.describeTopics(List.of(name)).allTopicNames(), topic(name)).get(name);
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
        Config config = await(admin.describeConfigs(List.of(resource)).all(), topic(name)).get(resource);

        List<PartitionInfo> partitions = t.partitions().stream()
                .map(p -> new PartitionInfo(p.partition(),
                        p.leader() == null || p.leader().isEmpty() ? null : p.leader().id(),
                        ids(p.replicas()), ids(p.isr())))
                .toList();
        List<ConfigValue> configs = EDITABLE_CONFIGS.stream()
                .map(config::get)
                .filter(Objects::nonNull)
                .map(e -> new ConfigValue(e.name(), e.value(),
                        e.source() != ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG))
                .toList();
        return new TopicDetail(name, t.isInternal(), isProtected(name), partitions, configs);
    }

    public TopicDetail create(CreateTopic req) {
        if (isProtected(req.name())) {
            throw new ResourceConflictException("El nombre " + req.name() + " está reservado para el sistema.");
        }
        Map<String, String> configs = req.configs() == null ? Map.of() : req.configs();
        checkEditable(configs.keySet());
        NewTopic topic = new NewTopic(req.name(), req.partitions(), req.replicationFactor().shortValue())
                .configs(configs);
        await(admin.createTopics(List.of(topic)).all(), topic(req.name()));
        log.info("Tópico creado: {} ({} particiones, RF {}, configs {})",
                req.name(), req.partitions(), req.replicationFactor(), configs);
        return get(req.name());
    }

    /** Valor vacío = quitar el override (vuelve al default del broker). */
    public TopicDetail updateConfigs(String name, Map<String, String> configs) {
        if (name.startsWith("_")) {
            throw new ResourceConflictException("Los tópicos internos de Kafka no se modifican.");
        }
        checkEditable(configs.keySet());
        List<AlterConfigOp> ops = new ArrayList<>();
        configs.forEach((key, value) -> ops.add(value == null || value.isBlank()
                ? new AlterConfigOp(new ConfigEntry(key, ""), AlterConfigOp.OpType.DELETE)
                : new AlterConfigOp(new ConfigEntry(key, value.trim()), AlterConfigOp.OpType.SET)));
        ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, name);
        await(admin.incrementalAlterConfigs(Map.of(resource, ops)).all(), topic(name));
        log.info("Configuración del tópico {} modificada: {}", name, configs);
        return get(name);
    }

    public TopicDetail addPartitions(String name, int totalPartitions) {
        if (isProtected(name)) {
            // Más particiones cambian qué partición le toca a cada clave: se pierde el orden por reserva/unidad
            throw new ResourceConflictException("No se cambian las particiones de un tópico del sistema.");
        }
        await(admin.createPartitions(Map.of(name, NewPartitions.increaseTo(totalPartitions))).all(), topic(name));
        log.info("Tópico {}: particiones aumentadas a {}", name, totalPartitions);
        return get(name);
    }

    public void delete(String name) {
        if (isProtected(name)) {
            throw new ResourceConflictException("El tópico " + name + " es del sistema y no se puede eliminar.");
        }
        await(admin.deleteTopics(List.of(name)).all(), topic(name));
        log.info("Tópico eliminado: {}", name);
    }

    private static void checkEditable(Set<String> keys) {
        List<String> notAllowed = keys.stream().filter(k -> !EDITABLE_CONFIGS.contains(k)).toList();
        if (!notAllowed.isEmpty()) {
            throw new IllegalArgumentException("Configuración no permitida: " + notAllowed
                    + ". Permitidas: " + EDITABLE_CONFIGS);
        }
    }

    private static List<Integer> ids(List<Node> nodes) {
        return nodes.stream().map(Node::id).toList();
    }

    private static String topic(String name) {
        return "El tópico " + name;
    }
}
