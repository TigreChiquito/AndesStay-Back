package cl.tigrechiquito.ms_andesstay_kafka_admin.dto;

import java.util.List;

/** Respuestas del administrador (solo lectura). */
public final class KafkaViews {

    private KafkaViews() {
    }

    public record Broker(int id, String host, int port, boolean controller) {
    }

    public record Cluster(String clusterId, List<Broker> brokers, int topics, int consumerGroups) {
    }

    public record TopicSummary(String name, int partitions, int replicationFactor,
                               boolean internal, boolean protectedTopic) {
    }

    public record PartitionInfo(int partition, Integer leader, List<Integer> replicas, List<Integer> isr) {
    }

    /** isDefault = el valor viene del broker, no hay override en el tópico. */
    public record ConfigValue(String name, String value, boolean isDefault) {
    }

    public record TopicDetail(String name, boolean internal, boolean protectedTopic,
                              List<PartitionInfo> partitions, List<ConfigValue> configs) {
    }

    public record GroupSummary(String groupId, String state, int members, long totalLag) {
    }

    public record Member(String clientId, String host, List<String> assignments) {
    }

    /** committed = null si el grupo nunca confirmó offset en esa partición. */
    public record PartitionLag(String topic, int partition, Long committed, long logEnd, long lag) {
    }

    public record GroupDetail(String groupId, String state, List<Member> members, List<PartitionLag> offsets,
                              long totalLag) {
    }

    public record ResetResult(String groupId, String topic, List<PartitionLag> newOffsets) {
    }
}
