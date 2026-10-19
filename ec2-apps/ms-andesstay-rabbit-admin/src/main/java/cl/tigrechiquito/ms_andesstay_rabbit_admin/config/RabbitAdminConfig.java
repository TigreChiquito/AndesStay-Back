package cl.tigrechiquito.ms_andesstay_rabbit_admin.config;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitAdmin explícito (en vez del AmqpAdmin autoconfigurado) para inyectarlo
 * por su tipo concreto en RabbitAdminService. No declara topología propia: este
 * micro solo administra la del clúster bajo demanda.
 */
@Configuration
public class RabbitAdminConfig {

    @Bean
    RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
