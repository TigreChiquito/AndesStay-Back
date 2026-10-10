# ms-andesstay-reservations

Microservicio dueño del ciclo de vida de una **reserva** de hospedaje. Es el corazón del sistema: expone la API REST de reservas, coordina los cupos con catalog y es el origen de **toda** la mensajería (eventos a Kafka y comandos a RabbitMQ).

## Responsabilidades

- Crear y consultar reservas, y hacerlas avanzar por una máquina de estados explícita.
- Descontar o devolver cupos en `ms-andesstay-catalog` por REST, de forma síncrona y con compensación.
- Publicar en **Kafka** (`reservations.events`) cada creación y cambio de estado, para report y audit.
- Publicar en **RabbitMQ** los comandos de notificación que correspondan, para notify.

## Máquina de estados

```
CREADA ──▶ CONFIRMADA ──▶ CHECKIN_PENDIENTE ──▶ EN_ESTADIA ──▶ CHECKOUT
   │            │                │
   └────────────┴────────────────┴──▶ CANCELADA
```

| Desde | Puede pasar a |
|---|---|
| `CREADA` | `CONFIRMADA`, `CANCELADA` |
| `CONFIRMADA` | `CHECKIN_PENDIENTE`, `CANCELADA` |
| `CHECKIN_PENDIENTE` | `EN_ESTADIA`, `CANCELADA` |
| `EN_ESTADIA` | `CHECKOUT` |
| `CHECKOUT`, `CANCELADA` | — (terminales) |

- La regla "no hay check-in sin confirmar" queda garantizada por la propia tabla de transiciones.
- La validación vive en el dominio (`ReservationStatus.canTransitionTo` y `Reservation.changeStatusTo`), no en el service.
- El parseo del estado es tolerante a tildes y mayúsculas: `"en_estadía"` se interpreta como `EN_ESTADIA`.
- Pedir el mismo estado en que ya está la reserva no es error: responde 200 sin hacer nada (no llama a catalog ni publica eventos).

## API REST

Base path: `/api/reservations`

| Método | Endpoint | Respuesta |
|---|---|---|
| `POST` | `/api/reservations` | **201** + `Location`. La reserva nace en `CREADA` |
| `GET` | `/api/reservations/{id}` | 200, o 404 |
| `PUT` | `/api/reservations/{id}/status` | 200, o 409 si la transición es inválida o no hay cupo |
| `POST` | `/api/reservations/{id}/cancel` | 200; 403 si un huésped intenta cancelar una reserva ajena; 409 si ya no es cancelable |
| `GET` | `/api/reservations?status=&from=&to=` | 200. Filtros opcionales; `from`/`to` filtran por fecha de check-in y deben ir juntos |

```json
POST /api/reservations
{
  "guestId": "guest-123",
  "guestName": "Ana Pérez",
  "unitId": 1,
  "checkInDate": "2026-12-01",
  "checkOutDate": "2026-12-05"
}
```

```json
PUT /api/reservations/1/status
{ "status": "CONFIRMADA" }
```

Respuesta (`ReservationResponse`): `id, guestId, guestName, unitId, checkInDate, checkOutDate, status, createdAt, updatedAt`.

### Errores (ProblemDetail, RFC 7807)

| Código | Cuándo |
|---|---|
| 400 | Payload inválido, `checkOutDate` no posterior a `checkInDate`, estado desconocido o unidad inexistente en catalog |
| 403 | Un huésped intenta cancelar una reserva que no es suya |
| 404 | La reserva no existe |
| 409 | Transición de estado inválida (incluye `from` y `to`), o la unidad no tiene cupo |
| 503 | catalog no responde al confirmar o cancelar |

> `guestId` viaja hoy en el body. Cuando se integre la identidad desde el BFF, debería sacarse del token.

### Cancelación por el huésped (`POST /{id}/cancel`)

Es un endpoint aparte de `PUT /status`, para que el rol Cliente pueda cancelar **sin** poder pasar a otros estados (confirmar, check-in, etc.):

- Lee la identidad desde los headers que agrega el BFF: `X-User-Id` (el `oid` del token) y `X-User-Roles`.
- **Recepcionista y Admin** pueden cancelar cualquier reserva.
- **Cualquier otro usuario** solo puede cancelar las reservas cuyo `guestId` coincida con su `X-User-Id`; si no, responde **403**.
- Después usa el mismo flujo de `changeStatus(CANCELADA)`: valida la transición, devuelve el cupo a catalog si estaba confirmada y publica los eventos.

Para que esta regla funcione, las reservas deben crearse con `guestId` = `localAccountId` de MSAL, que es lo que manda el front.

## Integración con catalog (REST síncrono)

`CatalogClient` llama a `${catalog.base-url}/api/units/{id}/reserve|release`:

| Transición | Llamada a catalog |
|---|---|
| → `CONFIRMADA` | `reserve`: descuenta un cupo. Si catalog responde 409, se aborta la confirmación |
| `CONFIRMADA` / `CHECKIN_PENDIENTE` → `CANCELADA` | `release`: devuelve el cupo |
| → `CHECKOUT` | `release`: la estadía terminó y el cupo vuelve al pool |
| `CREADA` → `CANCELADA` | ninguna (nunca se descontó cupo) |

La transición se valida **antes** de llamar a catalog, para no reservar un cupo en vano. Si después de reservar el cupo falla el guardado local, se **compensa** llamando a `release`.

No hay FK entre servicios: `unitId` es una referencia lógica a catalog.

## Mensajería

Toda la publicación ocurre en listeners `@TransactionalEventListener(AFTER_COMMIT)`: los mensajes salen **solo si la transacción hizo commit**. Así nunca se notifica algo que luego hace rollback.

### Kafka — `reservations.events`

- Se publica en la creación (`reservation.created`) y en cada cambio de estado (`reservation.status_changed`).
- Key = `reservationId`, para que todos los eventos de una reserva caigan en la misma partición y mantengan su orden.
- Productor con `acks=all` y sin type headers (`spring.json.add.type.headers=false`), para no acoplar a los consumidores con la clase Java.
- `max.block.ms=5000`: si Kafka no responde, el envío se rinde a los 5 s y el error queda en el log. La reserva **ya está guardada** y el request responde normalmente, sin quedar colgado hasta el timeout de 30 s del API Gateway.

```json
{
  "type": "reservation.status_changed",
  "eventId": "uuid",
  "timestamp": "2026-10-05T22:43:01Z",
  "reservationId": 1,
  "guestId": "guest-123",
  "unitId": 1,
  "checkInDate": "2026-12-01",
  "checkOutDate": "2026-12-05",
  "status": "CONFIRMADA"
}
```

El tópico se declara con 3 particiones y `andesstay.kafka.replicas` réplicas (`ANDESSTAY_KAFKA_REPLICAS`): 1 por defecto en dev, 3 en ec2-kafka.

### RabbitMQ — comandos de notificación

Este servicio es dueño de la topología (`RabbitTopologyConfig`). notify la re-declara de forma idempotente.

- Exchanges: `cmd.direct`, `cmd.topic` y `cmd.dead.dlx` (dead-letter).
- Colas: `q.cmd.notification`, `q.cmd.housekeeping`, `q.cmd.voucher`, cada una con su `.dlq`.
- Se publica al exchange **topic** con el `CommandEnvelope` `{type, eventId, timestamp, traceId, correlationId, payload}`. `correlationId` = `reservation-<id>`.

| Estado | Aviso al huésped (`notification.*`) | Aviso al personal (`housekeeping.#`) | Otros |
|---|---|---|---|
| CREADA (al crear) | `notification.created` | `housekeeping.new_reservation` | — |
| CONFIRMADA | `notification.confirmed` | — | `voucher.gen` |
| CHECKIN_PENDIENTE | — | `housekeeping.prepare_unit` | — |
| EN_ESTADIA | — | — | — |
| CHECKOUT | `notification.checkout` | `housekeeping.clean_unit` | — |
| CANCELADA | `notification.cancelled` | `housekeeping.reservation_cancelled` | — |

Lo que hace notify con cada uno está en su [README](../ms-andesstay-notify/README.md).

- Un fallo de RabbitMQ no se convierte en error HTTP: el cambio ya está guardado, así que el error queda en el log.
- `spring.rabbitmq.connection-timeout: 5s`, para que un ec2-mq que no responde no deje el request colgado hasta el timeout de 30 s del API Gateway.
- El productor usa *publisher confirms* y *publisher returns*.

## Estructura

```
ms_andesstay_reservations/
├── domain/        Reservation · ReservationStatus · excepciones de dominio
├── repository/    ReservationRepository
├── dto/           CreateReservationRequest · UpdateStatusRequest · ReservationResponse
├── service/       ReservationService (transiciones + coordinación con catalog)
├── client/        CatalogClient · UnitNotAvailableException · CatalogUnavailableException
├── controller/    ReservationController · GlobalExceptionHandler
└── messaging/
    ├── ReservationCreatedEvent · ReservationStatusChangedEvent   (eventos internos)
    ├── kafka/     KafkaEventListener · ReservationEventPublisher · KafkaTopicConfig · ...
    └── rabbit/    ReservationEventListener · ReservationCommandPublisher · RabbitTopologyConfig · ...
```

## Configuración

`src/main/resources/application.yml` (valores para desarrollo local). En el contenedor se sobrescriben por variables de entorno desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8081` | — |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/reservations_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.password` | `andesstay` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.rabbitmq.host` / `port` | `localhost:5672` | `SPRING_RABBITMQ_HOST` / `_PORT` |
| `spring.rabbitmq.password` | `andesstay` | `SPRING_RABBITMQ_PASSWORD` |
| `spring.kafka.bootstrap-servers` | `localhost:9092` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` |
| `catalog.base-url` | `http://localhost:8082` | `CATALOG_BASE_URL` |
| `andesstay.kafka.replicas` | `1` | `ANDESSTAY_KAFKA_REPLICAS` |

`ddl-auto: update` crea y actualiza las tablas automáticamente. El log de SQL (`DEBUG`/`TRACE`) está activo para aprendizaje.

> Spring Boot 4: el `RestClient.Builder` que usa `CatalogClient` requiere `spring-boot-starter-restclient`. Sin ese starter, el micro no arranca.

## Ejecutar localmente

Requiere PostgreSQL (`reservations_db`), Kafka, RabbitMQ y catalog en `localhost`.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8081/actuator/health`.
