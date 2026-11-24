package cl.tigrechiquito.ms_andesstay_kafka_admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.AddPartitions;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.CreateTopic;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.ResetOffsets;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.UpdateConfigs;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.Cluster;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.GroupDetail;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.GroupSummary;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.ResetResult;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.TopicDetail;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.TopicSummary;
import cl.tigrechiquito.ms_andesstay_kafka_admin.service.ClusterService;
import cl.tigrechiquito.ms_andesstay_kafka_admin.service.ConsumerGroupService;
import cl.tigrechiquito.ms_andesstay_kafka_admin.service.TopicAdminService;
import jakarta.validation.Valid;

/** API del administrador de Kafka. El BFF la expone solo al rol Admin. */
@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final ClusterService cluster;
    private final TopicAdminService topics;
    private final ConsumerGroupService groups;

    public KafkaAdminController(ClusterService cluster, TopicAdminService topics, ConsumerGroupService groups) {
        this.cluster = cluster;
        this.topics = topics;
        this.groups = groups;
    }

    @GetMapping("/cluster")
    public Cluster cluster() {
        return cluster.overview();
    }

    @GetMapping("/topics")
    public List<TopicSummary> topics() {
        return topics.list();
    }

    @GetMapping("/topics/{name}")
    public TopicDetail topic(@PathVariable String name) {
        return topics.get(name);
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public TopicDetail createTopic(@Valid @RequestBody CreateTopic req) {
        return topics.create(req);
    }

    @PutMapping("/topics/{name}/configs")
    public TopicDetail updateConfigs(@PathVariable String name, @Valid @RequestBody UpdateConfigs req) {
        return topics.updateConfigs(name, req.configs());
    }

    @PostMapping("/topics/{name}/partitions")
    public TopicDetail addPartitions(@PathVariable String name, @Valid @RequestBody AddPartitions req) {
        return topics.addPartitions(name, req.totalPartitions());
    }

    @DeleteMapping("/topics/{name}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable String name) {
        topics.delete(name);
    }

    @GetMapping("/groups")
    public List<GroupSummary> groups() {
        return groups.list();
    }

    @GetMapping("/groups/{groupId}")
    public GroupDetail group(@PathVariable String groupId) {
        return groups.get(groupId);
    }

    @PostMapping("/groups/{groupId}/reset-offsets")
    public ResetResult resetOffsets(@PathVariable String groupId, @Valid @RequestBody ResetOffsets req) {
        return groups.resetOffsets(groupId, req);
    }
}
