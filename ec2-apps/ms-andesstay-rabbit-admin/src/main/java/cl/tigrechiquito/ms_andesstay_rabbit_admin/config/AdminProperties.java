package cl.tigrechiquito.ms_andesstay_rabbit_admin.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del administrador (prefijo "rabbit" en application.yml).
 *
 * @param management       API HTTP de la Management UI
 * @param protectedPrefixes prefijos de nombres que no se pueden crear ni eliminar
 */
@ConfigurationProperties(prefix = "rabbit")
public record AdminProperties(Management management, List<String> protectedPrefixes) {

    /**
     * @param url       URL que usa este micro (IP privada de ec2-mq)
     * @param publicUrl URL que abre el navegador (IP pública de ec2-mq)
     */
    public record Management(String url, String username, String password, String publicUrl) {
    }

    /** ¿El nombre pertenece a la topología del sistema o a RabbitMQ (amq.*)? */
    public boolean isProtected(String name) {
        return name != null && protectedPrefixes.stream().anyMatch(name::startsWith);
    }
}
