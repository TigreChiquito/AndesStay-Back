package cl.tigrechiquito.ms_andesstay_kafka_admin.service;

import static cl.tigrechiquito.ms_andesstay_kafka_admin.service.KafkaCalls.await;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsSpec;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceNotFoundException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.ResetOffsets;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.ResetTo;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.GroupDetail;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.GroupSummary;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.Member;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.PartitionLag;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.ResetResult;

/**
 * Consumer groups: listar con su lag, ver miembros y offsets por partición, y
 * mover offsets (replay) como "kafka-consumer-groups.sh --reset-offsets".
 */
@Service
public class ConsumerGroupService {

    private static final Logger log = LoggerFactory.getLogger(ConsumerGroupService.class);

    private final Admin admin;

    public ConsumerGroupService(Admin admin) {
        this.admin = admin;
    }

    public List<GroupSummary> list() {
        List<String> ids = await(admin.listGroups(ListGroupsOptions.forConsumerGroups()).all(), "Los grupos")
                .stream().map(GroupListing::groupId).sorted().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<String, ConsumerGroupDescription> descriptions =
                await(admin.describeConsumerGroups(ids).all(), "Los grupos");
        Map<String, Map<TopicPartition, OffsetAndMetadata>> committed = await(admin.listConsumerGroupOffsets(
                ids.stream().collect(Collectors.toMap(Function.identity(), id -> new ListConsumerGroupOffsetsSpec())))
                .all(), "Los grupos");
        Map<TopicPartition, Long> logEnd = logEnd(committed.values().stream()
                .flatMap(m -> m.keySet().stream()).collect(Collectors.toSet()));

        return ids.stream().map(id -> {
            ConsumerGroupDescription d = descriptions.get(id);
            long lag = totalLag(lags(committed.getOrDefault(id, Map.of()), Set.of(), logEnd));
            return new GroupSummary(id, d.groupState().toString(), d.members().size(), lag);
        }).toList();
    }

    public GroupDetail get(String groupId) {
        ConsumerGroupDescription d = describe(groupId);
        Map<TopicPartition, OffsetAndMetadata> committed = committed(groupId);
        Set<TopicPartition> assigned = d.members().stream()
                .flatMap(m -> m.assignment().topicPartitions().stream())
                .collect(Collectors.toSet());

        Set<TopicPartition> all = new java.util.HashSet<>(committed.keySet());
        all.addAll(assigned);
        List<PartitionLag> offsets = lags(committed, assigned, logEnd(all));

        List<Member> members = d.members().stream()
                .map(m -> new Member(m.clientId(), m.host(), m.assignment().topicPartitions().stream()
                        .map(tp -> tp.topic() + "-" + tp.partition()).sorted().toList()))
                .toList();
        return new GroupDetail(groupId, d.groupState().toString(), members, offsets, totalLag(offsets));
    }

    /**
     * Mueve los offsets del grupo en todas las particiones de un tópico. Kafka solo
     * lo permite con el grupo sin consumidores (hay que detener el micro antes).
     */
    public ResetResult resetOffsets(String groupId, ResetOffsets req) {
        ConsumerGroupDescription d = describe(groupId);
        if (!d.members().isEmpty()) {
            throw new ResourceConflictException("El grupo " + groupId + " tiene " + d.members().size()
                    + " consumidores activos: detén el microservicio antes de mover los offsets.");
        }
        if (req.to() == ResetTo.DATETIME && req.datetime() == null) {
            throw new IllegalArgumentException("Para DATETIME indica datetime (ej. 2026-11-21T12:00:00Z).");
        }

        Set<TopicPartition> partitions = await(admin.describeTopics(List.of(req.topic())).allTopicNames(),
                "El tópico " + req.topic()).get(req.topic()).partitions().stream()
                .map(p -> new TopicPartition(req.topic(), p.partition()))
                .collect(Collectors.toSet());
        Map<TopicPartition, Long> logEnd = logEnd(partitions);
        Map<TopicPartition, Long> target = switch (req.to()) {
            case LATEST -> logEnd;
            case EARLIEST -> offsets(partitions, OffsetSpec.earliest());
            // -1 = no hay mensajes desde esa fecha: se salta al final
            case DATETIME -> offsets(partitions, OffsetSpec.forTimestamp(req.datetime().toEpochMilli()))
                    .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                            e -> e.getValue() < 0 ? logEnd.get(e.getKey()) : e.getValue()));
        };

        Map<TopicPartition, OffsetAndMetadata> newOffsets = new HashMap<>();
        target.forEach((tp, offset) -> newOffsets.put(tp, new OffsetAndMetadata(offset)));
        await(admin.alterConsumerGroupOffsets(groupId, newOffsets).all(), "El grupo " + groupId);
        log.info("Offsets del grupo {} en {} movidos a {}{}", groupId, req.topic(), req.to(),
                req.datetime() == null ? "" : " " + req.datetime());

        return new ResetResult(groupId, req.topic(), lags(newOffsets, Set.of(), logEnd));
    }

    private ConsumerGroupDescription describe(String groupId) {
        ConsumerGroupDescription d = await(admin.describeConsumerGroups(List.of(groupId)).all(),
                "El grupo " + groupId).get(groupId);
        // Un grupo desconocido puede venir como DEAD en vez de error
        if (d == null || d.groupState() == GroupState.DEAD) {
            throw new ResourceNotFoundException("El grupo " + groupId + " no existe.");
        }
        return d;
    }

    private Map<TopicPartition, OffsetAndMetadata> committed(String groupId) {
        return await(admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata(), "El grupo " + groupId);
    }

    private Map<TopicPartition, Long> logEnd(Set<TopicPartition> partitions) {
        return offsets(partitions, OffsetSpec.latest());
    }

    private Map<TopicPartition, Long> offsets(Set<TopicPartition> partitions, OffsetSpec spec) {
        if (partitions.isEmpty()) {
            return Map.of();
        }
        Map<TopicPartition, OffsetSpec> request = partitions.stream()
                .collect(Collectors.toMap(Function.identity(), tp -> spec));
        return await(admin.listOffsets(request).all(), "Los offsets").entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }

    /** lag = fin del log - offset confirmado (sin confirmar: todo el log está pendiente). */
    static List<PartitionLag> lags(Map<TopicPartition, OffsetAndMetadata> committed,
                                   Set<TopicPartition> alsoInclude, Map<TopicPartition, Long> logEnd) {
        Set<TopicPartition> all = new TreeSet<>(Comparator.comparing(TopicPartition::topic)
                .thenComparingInt(TopicPartition::partition));
        all.addAll(committed.keySet());
        all.addAll(alsoInclude);
        return all.stream().map(tp -> {
            OffsetAndMetadata c = committed.get(tp);
            long end = logEnd.getOrDefault(tp, 0L);
            Long offset = c == null ? null : c.offset();
            return new PartitionLag(tp.topic(), tp.partition(), offset, end,
                    Math.max(0, end - (offset == null ? 0 : offset)));
        }).toList();
    }

    private static long totalLag(List<PartitionLag> lags) {
        return lags.stream().mapToLong(PartitionLag::lag).sum();
    }
}
