# ms-andesstay-audit

Microservicio de **auditoría**. Guarda un historial **inmutable** (*append-only*) de todo lo que le pasa a cada reserva, construido a partir de los eventos de Kafka, para que el rol Auditor pueda consultar la trazabilidad completa: quién, qué y cuándo.

## Responsabilidades

- Consumir `reservations.events` y guardar **una fila por evento** en `audit_events`. Nunca hace update ni delete.
- Deduplicar por `eventId`, para que una reentrega de Kafka no duplique el historial.
- Exponer la línea de tiempo de una reserva y los eventos recientes.
- Enviar a su propia DLT los mensajes que fallan tras reintentar.

## API REST

Base path: `/api/audit`. A través del BFF, solo los roles **`Auditor`** y **`Admin`** tienen acceso.

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/audit/reservations/{reservationId}` | Línea de tiempo de una reserva, del evento más antiguo al más reciente |
| `GET` | `/api/audit/events?limit=50` | Últimos `limit` eventos (por defecto 50) de todas las reservas, del más reciente al más antiguo |

```json
GET /api/audit/reservations/1
[
  { "id": 1, "eventId": "uuid", "type": "reservation.created",
    "reservationId": 1, "unitId": 4, "guestId": "guest-123",
    "status": "CREADA", "occurredAt": "2026-10-05T22:40:00Z", "recordedAt": "2026-10-05T22:40:01Z" },
  { "id": 2, "eventId": "uuid", "type": "reservation.status_changed",
    "reservationId": 1, "unitId": 4, "guestId": "guest-123",
    "status": "CONFIRMADA", "occurredAt": "2026-10-05T22:43:01Z", "recordedAt": "2026-10-05T22:43:01Z" }
]
```

- `occurredAt` es cuándo ocurrió el evento en reservations (el `timestamp` del mensaje).
- `recordedAt` es cuándo audit lo guardó.

## Consumo de `reservations.events`

`AuditEventConsumer`:

1. Si ya existe una fila con ese `eventId` → la descarta (idempotencia).
2. Si no, inserta una fila nueva. Además, `event_id` tiene una restricción `UNIQUE` en la tabla, como segunda línea de defensa.

| Configuración | Valor |
|---|---|
| `group-id` | `ms-andesstay-audit`, distinto al de report (fan-out: ambos reciben todo) |
| `auto-offset-reset` | `earliest` |
| Deserializador | `ErrorHandlingDeserializer` → `JacksonJsonDeserializer`, con tipo por defecto `ReservationEventMessage` |
| Reintentos | `DefaultErrorHandler` + `FixedBackOff(1s, 2 reintentos)` = 3 intentos |
| DLT | `reservations.events.audit.DLT` (una por consumidor, como pide la pauta), en la misma partición que el original |

La DLT se declara con `andesstay.kafka.replicas` réplicas (3 en ec2-kafka, mediante `ANDESSTAY_KAFKA_REPLICAS`).

> **Pendiente de la pauta:** el "desde dónde" (IP u origen de la petición) no viaja en `reservations.events`. Ese dato solo lo conoce el BFF, así que habría que agregarlo en el BFF o en un tópico propio.

## Estructura

```
ms_andesstay_audit/
├── domain/       AuditEvent (tabla audit_events, append-only)
├── repository/   AuditEventRepository
├── messaging/    KafkaConstants · ReservationEventMessage · KafkaConsumerConfig · AuditEventConsumer
├── dto/          AuditEventResponse
├── service/      AuditService
└── controller/   AuditController
```

## Configuración

`src/main/resources/application.yml`; en el contenedor se sobrescribe desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8085` | — |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/audit_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.password` | `andesstay` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `spring.kafka.consumer.group-id` | `ms-andesstay-audit` | — |
| `andesstay.kafka.replicas` | `1` | `ANDESSTAY_KAFKA_REPLICAS` |

## Ejecutar localmente

Requiere PostgreSQL (`audit_db`) y Kafka en `localhost:9092`.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8085/actuator/health`.
