package cl.tigrechiquito.ms_andesstay_kafka_admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.junit.jupiter.api.Test;

import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.KafkaUnavailableException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.domain.ResourceNotFoundException;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaRequests.CreateTopic;
import cl.tigrechiquito.ms_andesstay_kafka_admin.dto.KafkaViews.PartitionLag;

class KafkaAdminServicesTest {

    private final Admin admin = mock(Admin.class);
    private final TopicAdminService topics = new TopicAdminService(admin, List.of("reservations.", "units.", "_"));

    @Test
    void losTopicosDelSistemaNoSeEliminanNiReparticionan() {
        assertThatThrownBy(() -> topics.delete("reservations.events")).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> topics.addPartitions("units.state", 6)).isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> topics.updateConfigs("__consumer_offsets", Map.of("retention.ms", "1")))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> topics.create(new CreateTopic("units.copia", 1, 1, null)))
                .isInstanceOf(ResourceConflictException.class);
        verifyNoInteractions(admin);
    }

    @Test
    void soloSePermitenLasConfiguracionesDeLaListaBlanca() {
        assertThatThrownBy(() -> topics.updateConfigs("pagos", Map.of("unclean.leader.election.enable", "true")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unclean.leader.election.enable");
        verifyNoInteractions(admin);
    }

    @Test
    void traduceLosErroresDeKafka() {
        assertThat(KafkaCalls.translate(new TopicExistsException("x"), "T")).isInstanceOf(ResourceConflictException.class);
        assertThat(KafkaCalls.translate(new UnknownTopicOrPartitionException("x"), "T")).isInstanceOf(ResourceNotFoundException.class);
        assertThat(KafkaCalls.translate(new TimeoutException("x"), "T")).isInstanceOf(KafkaUnavailableException.class);
        assertThat(KafkaCalls.translate(new InvalidPartitionsException("x"), "T")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void calculaElLagPorParticion() {
        TopicPartition p0 = new TopicPartition("pagos", 0);
        TopicPartition p1 = new TopicPartition("pagos", 1);
        List<PartitionLag> lags = ConsumerGroupService.lags(
                Map.of(p0, new OffsetAndMetadata(7)), Set.of(p1), Map.of(p0, 10L, p1, 4L));

        assertThat(lags).containsExactly(
                new PartitionLag("pagos", 0, 7L, 10, 3),
                new PartitionLag("pagos", 1, null, 4, 4));   // sin offset confirmado: todo pendiente
    }
}
