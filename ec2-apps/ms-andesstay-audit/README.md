# ms-andesstay-audit

Microservicio de **auditoría** (solo lectura). Mantiene un historial inmutable de todo lo que le pasa a cada reserva, construido a partir de los eventos que produce `ms-andesstay-reservations` en Kafka, para que un rol Auditor pueda consultar la trazabilidad completa.

## Responsabilidades

- Consumir el tópico Kafka `reservations.events` y guardar cada evento como una fila de historial (`AuditEvent`), con deduplicación por `eventId`.
- Exponer la línea de tiempo de una reserva y el listado de eventos recientes vía API REST.
- Enviar a una dead-letter topic (DLT) propia los mensajes que fallan tras reintentar, sin bloquear el resto del consumo.

Este servicio **no expone API de escritura**: es un consumidor puro de eventos.

## API REST

Base path: `/api/audit`

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/audit/reservations/{reservationId}` | Línea de tiempo completa de una reserva |
| `GET` | `/api/audit/events?limit=50` | Últimos eventos registrados (todas las reservas) |

## Mensajería — consumidor de `reservations.events`

- `group-id` propio (`ms-andesstay-audit`), distinto al de `ms-andesstay-report`, para que ambos reciban todos los eventos de forma independiente (fan-out).
- El `value-deserializer` está envuelto en `ErrorHandlingDeserializer` para que un mensaje corrupto no rompa el loop de consumo.
- `DefaultErrorHandler` con `FixedBackOff` (1s, 2 reintentos) y `DeadLetterPublishingRecoverer`: si tras 3 intentos el mensaje sigue fallando, se publica en la DLT propia de este servicio, `reservations.events.audit.DLT`.

Tipos de evento consumidos: `reservation.created`, `reservation.status_changed`.

## Configuración

Ver `src/main/resources/Application.yml`.

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `server.port` | `8085` | Puerto HTTP |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/audit_db` | Base de datos propia |
| `spring.datasource.password` | `andesstay` | Sobrescribible con `DB_PASSWORD` |
| `spring.kafka.consumer.group-id` | `ms-andesstay-audit` | Grupo de consumo Kafka |

`spring.jpa.hibernate.ddl-auto=update` está pensado solo para desarrollo.

## Ejecutar localmente

Requiere PostgreSQL (`audit_db`), y Kafka corriendo en `localhost:9092` con el tópico `reservations.events` (lo produce `ms-andesstay-reservations`).

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8085/actuator/health`.

## Tests

```bash
./mvnw test
```
