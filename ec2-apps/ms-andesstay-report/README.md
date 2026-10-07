# ms-andesstay-report

Microservicio de **reportería**. Mantiene un *read model* propio con el estado actual de cada reserva, construido a partir de los eventos de Kafka, y expone KPIs agregados. Es el lado de lectura de un patrón **CQRS**: nunca recibe escrituras por REST.

## Responsabilidades

- Consumir `reservations.events` y proyectar cada evento en la tabla `reservation_projection` (una fila por reserva, siempre con el estado más reciente).
- Exponer indicadores: total de reservas, estadías activas, conteo por estado y por unidad.
- Enviar a una DLT los mensajes que fallan tras reintentar, sin bloquear el resto del consumo.

## API REST

Base path: `/api/reports`. A través del BFF, solo el rol **`Admin`** tiene acceso.

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/reports/summary` | Resumen completo (ver abajo) |
| `GET` | `/api/reports/by-status` | Mapa `estado → cantidad` |
| `GET` | `/api/reports/by-unit` | Lista `[{unitId, total}]` |

```json
GET /api/reports/summary
{
  "totalReservations": 15,
  "activeStays": 3,
  "byStatus": { "CONFIRMADA": 4, "EN_ESTADIA": 3, "CHECKOUT": 2, "...": 0 },
  "byUnit": [ { "unitId": 1, "total": 2 }, { "unitId": 4, "total": 1 } ]
}
```

- `activeStays` es la cantidad de reservas en `EN_ESTADIA`.
- Las agregaciones se hacen en la base (`GROUP BY`), no en memoria.

## Consumo de `reservations.events`

`ReservationEventConsumer` hace un **upsert** por `reservationId`:

1. Busca la proyección de la reserva, o la crea si no existe.
2. Si el evento es **más antiguo** que el último aplicado (`timestamp < lastEventAt`), lo descarta. Así, reentregas o eventos fuera de orden no pisan un estado más nuevo.
3. Actualiza `unitId`, fechas, `status` y `lastEventAt`.

| Configuración | Valor |
|---|---|
| `group-id` | `ms-andesstay-report`, distinto al de audit (fan-out) |
| `auto-offset-reset` | `earliest`: un grupo nuevo lee el tópico desde el inicio |
| Deserializador | `ErrorHandlingDeserializer` → `JacksonJsonDeserializer`, con tipo por defecto `ReservationEventMessage` (el productor no envía type headers) |
| Reintentos | `DefaultErrorHandler` + `FixedBackOff(1s, 2 reintentos)` = 3 intentos |
| DLT | `reservations.events.DLT`, en la misma partición que el mensaje original |

La DLT se declara con 3 particiones y `andesstay.kafka.replicas` réplicas (3 en ec2-kafka, mediante `ANDESSTAY_KAFKA_REPLICAS`).

> Para mantener la simetría con audit (`reservations.events.audit.DLT`), convendría renombrar esta DLT a `reservations.events.report.DLT`.

## Estructura

```
ms_andesstay_report/
├── domain/       ReservationProjection
├── repository/   ReservationProjectionRepository · StatusCount · UnitCount
├── messaging/    KafkaConstants · ReservationEventMessage · KafkaConsumerConfig · ReservationEventConsumer
├── dto/          ReportSummaryResponse · UnitCountResponse
├── service/      ReportService
└── controller/   ReportController
```

## Configuración

`src/main/resources/application.yml`; en el contenedor se sobrescribe desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8084` | — |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/report_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.password` | `andesstay` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `spring.kafka.consumer.group-id` | `ms-andesstay-report` | — |
| `andesstay.kafka.replicas` | `1` | `ANDESSTAY_KAFKA_REPLICAS` |

## Ejecutar localmente

Requiere PostgreSQL (`report_db`) y Kafka en `localhost:9092`. Para ver datos, reservations tiene que estar publicando eventos.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8084/actuator/health`.

> Las reservas creadas mientras Kafka estaba caído no aparecen aquí: sus eventos nunca se publicaron. Para cargar datos iniciales sin Kafka, usa [`../sql/seed-report.sql`](../sql/README.md).
