# ec2-apps

Microservicios de AndesStay pensados para desplegarse como instancias independientes (por ejemplo, una por EC2 o contenedor). Cada subcarpeta es un proyecto Maven autocontenido: tiene su propio `pom.xml`, su propio Maven Wrapper (`mvnw`) y no depende de un módulo padre compartido dentro del repo.

## Servicios

- [`ms-andesstay-reservations`](ms-andesstay-reservations/README.md) — dueño del ciclo de vida de la reserva; publica eventos a Kafka y comandos a RabbitMQ.
- [`ms-andesstay-catalog`](ms-andesstay-catalog/README.md) — catálogo de unidades de hospedaje y su disponibilidad.
- [`ms-andesstay-notify`](ms-andesstay-notify/README.md) — envía notificaciones (email, housekeeping, voucher) consumiendo comandos de RabbitMQ.
- [`ms-andesstay-report`](ms-andesstay-report/README.md) — read model de reportería, construido a partir de los eventos de Kafka.
- [`ms-andesstay-audit`](ms-andesstay-audit/README.md) — read model de auditoría/trazabilidad, construido a partir de los eventos de Kafka.

Ver el [README raíz](../README.md) para el diagrama de arquitectura y cómo se comunican entre sí.
