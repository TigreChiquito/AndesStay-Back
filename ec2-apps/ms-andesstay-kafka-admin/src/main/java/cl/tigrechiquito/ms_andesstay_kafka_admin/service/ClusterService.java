package cl.tigrechiquito.ms_andesstay_kafka_admin.service;

import static cl.tigrechiquito.ms_andesstay_kafka_admin.service.KafkaCalls.await;

import java.util.Comparator;
import java.util.List;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.common.Node;
import org.springframework.stereotype.Service;

import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.Broker;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.Cluster;

/** Resumen del clúster: brokers, controlador y cuántos tópicos y grupos hay. */
@Service
public class ClusterService {

    private final Admin admin;

    public ClusterService(Admin admin) {
        this.admin = admin;
    }

    public Cluster overview() {
        DescribeClusterResult cluster = admin.describeCluster();
        Node controller = await(cluster.controller(), "El clúster");
        List<Broker> brokers = await(cluster.nodes(), "El clúster").stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> new Broker(n.id(), n.host(), n.port(), controller != null && controller.id() == n.id()))
                .toList();
        int topics = await(admin.listTopics().names(), "Los tópicos").size();   // sin los internos
        int groups = await(admin.listGroups(ListGroupsOptions.forConsumerGroups()).all(), "Los grupos").size();
        return new Cluster(await(cluster.clusterId(), "El clúster"), brokers, topics, groups);
    }
}
