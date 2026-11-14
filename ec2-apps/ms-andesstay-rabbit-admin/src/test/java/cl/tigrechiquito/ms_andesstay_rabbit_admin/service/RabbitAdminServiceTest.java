package cl.tigrechiquito.ms_andesstay_rabbit_admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import cl.tigrechiquito.ms_andesstay_rabbit_admin.config.AdminProperties;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ResourceConflictException;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.domain.ResourceNotFoundException;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.BindingRequest;
import cl.tigrechiquito.ms_andesstay_rabbit_admin.dto.CreateQueueRequest;

class RabbitAdminServiceTest {

    private RabbitAdmin rabbitAdmin;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        rabbitAdmin = mock(RabbitAdmin.class);
        AdminProperties props = new AdminProperties(
                new AdminProperties.Management("http://mq:15672", "u", "p", "http://public:15672"),
                List.of("cmd.", "q.cmd.", "amq."));
        service = new RabbitAdminService(rabbitAdmin, props);
    }

    @Test
    void creaColaConDlqYRetencion() {
        // no existe antes de crearla; después sí
        when(rabbitAdmin.getQueueInfo("q.demo"))
                .thenReturn(null)
                .thenReturn(new QueueInformation("q.demo", 0, 0));
        when(rabbitAdmin.getQueueInfo("q.demo.dlq")).thenReturn(null);

        var info = service.createQueue(new CreateQueueRequest("q.demo", true, true, 60000L, 100, "reject-publish-dlx"));

        ArgumentCaptor<Queue> queues = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin, org.mockito.Mockito.times(2)).declareQueue(queues.capture());
        Queue dlq = queues.getAllValues().get(0);
        Queue main = queues.getAllValues().get(1);
        assertThat(dlq.getName()).isEqualTo("q.demo.dlq");
        assertThat(main.getName()).isEqualTo("q.demo");
        assertThat(main.getArguments())
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "q.demo.dlq")
                .containsEntry("x-message-ttl", 60000L)
                .containsEntry("x-max-length", 100)
                .containsEntry("x-overflow", "reject-publish-dlx");
        verify(rabbitAdmin).declareBinding(any(Binding.class));
        assertThat(info.name()).isEqualTo("q.demo");
    }

    @Test
    void colaSinOpcionesNoLlevaArgumentos() {
        when(rabbitAdmin.getQueueInfo("q.simple"))
                .thenReturn(null)
                .thenReturn(new QueueInformation("q.simple", 0, 0));

        service.createQueue(new CreateQueueRequest("q.simple", null, false, null, null, null));

        ArgumentCaptor<Queue> queue = ArgumentCaptor.forClass(Queue.class);
        verify(rabbitAdmin).declareQueue(queue.capture());
        assertThat(queue.getValue().isDurable()).isTrue();
        assertThat(queue.getValue().getArguments()).isEmpty();
        verify(rabbitAdmin, never()).declareBinding(any());
    }

    @Test
    void noCreaUnaColaQueYaExiste() {
        when(rabbitAdmin.getQueueInfo("q.demo")).thenReturn(new QueueInformation("q.demo", 3, 1));

        assertThatThrownBy(() -> service.createQueue(new CreateQueueRequest("q.demo", true, false, null, null, null)))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("ya existe");
        verify(rabbitAdmin, never()).declareQueue(any());
    }

    @Test
    void noPermiteTocarLaTopologiaDelSistema() {
        assertThatThrownBy(() -> service.deleteQueue("q.cmd.notification"))
                .isInstanceOf(ResourceConflictException.class)
                .hasMessageContaining("protegido");
        assertThatThrownBy(() -> service.deleteExchange("cmd.topic"))
                .isInstanceOf(ResourceConflictException.class);
        assertThatThrownBy(() -> service.createQueue(new CreateQueueRequest("amq.mia", true, false, null, null, null)))
                .isInstanceOf(ResourceConflictException.class);
        verify(rabbitAdmin, never()).deleteQueue(any());
        verify(rabbitAdmin, never()).deleteExchange(any());
    }

    @Test
    void eliminarUnaColaInexistenteEs404() {
        when(rabbitAdmin.getQueueInfo("q.fantasma")).thenReturn(null);

        assertThatThrownBy(() -> service.deleteQueue("q.fantasma"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(rabbitAdmin, never()).deleteQueue(any());
    }

    @Test
    void eliminaUnaColaExistente() {
        when(rabbitAdmin.getQueueInfo("q.demo")).thenReturn(new QueueInformation("q.demo", 0, 0));

        service.deleteQueue("q.demo");

        verify(rabbitAdmin).deleteQueue(eq("q.demo"));
    }

    @Test
    void eliminarUnaColaConDlqEliminaTambienLaDlq() {
        when(rabbitAdmin.getQueueInfo("q.demo")).thenReturn(new QueueInformation("q.demo", 0, 0));
        when(rabbitAdmin.getQueueInfo("q.demo.dlq")).thenReturn(new QueueInformation("q.demo.dlq", 2, 0));

        service.deleteQueue("q.demo");

        verify(rabbitAdmin).deleteQueue(eq("q.demo"));
        verify(rabbitAdmin).deleteQueue(eq("q.demo.dlq"));
    }

    @Test
    void eliminarUnaColaSinDlqNoBorraOtraCosa() {
        when(rabbitAdmin.getQueueInfo("q.demo")).thenReturn(new QueueInformation("q.demo", 0, 0));
        when(rabbitAdmin.getQueueInfo("q.demo.dlq")).thenReturn(null);

        service.deleteQueue("q.demo");

        verify(rabbitAdmin).deleteQueue(eq("q.demo"));
        verify(rabbitAdmin, never()).deleteQueue(eq("q.demo.dlq"));
    }

    @Test
    void noSeTocanLosBindingsDeColasDelSistema() {
        assertThatThrownBy(() -> service.createBinding(new BindingRequest("cmd.topic", "q.cmd.voucher", "x")))
                .isInstanceOf(ResourceConflictException.class);
        verify(rabbitAdmin, never()).declareBinding(any());
    }
}
