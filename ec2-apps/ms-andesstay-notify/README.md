# ms-andesstay-notify

Microservicio de **avisos in-app**. Consume los comandos que `ms-andesstay-reservations` publica en RabbitMQ, los convierte en notificaciones guardadas en su propia base (`notify_db`) y las expone por API para la campanita del front.

## Las 3 colas

| Cola | Para quién | Qué hace |
|---|---|---|
| `q.cmd.notification` | **Huésped** (Cliente) | Guarda un aviso para el dueño de la reserva (`recipientId = guestId`) |
| `q.cmd.housekeeping` | **Personal** (Recepcionista y Admin) | Guarda un aviso en la bandeja compartida del personal |
| `q.cmd.voucher` | — | Generación del voucher. **Pendiente:** hoy solo escribe en el log |

## Qué genera cada cambio de reserva

| Reserva | Aviso al huésped | Aviso al personal |
|---|---|---|
| Creada | **Recibimos tu reserva**: registrada y pendiente de confirmación | **Nueva reserva por confirmar**: quién, qué unidad y qué fechas |
| → CONFIRMADA | **Reserva confirmada** (+ comando `voucher.gen`) | — |
| → CHECKIN_PENDIENTE | — | **Preparar unidad X**: check-in de quién y cuándo |
| → EN_ESTADIA | — | — |
| → CHECKOUT | **Gracias por tu estadía** | **Limpiar unidad X** |
| → CANCELADA | **Reserva cancelada** | **Reserva cancelada**: cuál y de quién |

Los textos se arman en [`NotificationService`](src/main/java/cl/tigrechiquito/ms_andesstay_notify/service/NotificationService.java) con los datos que trae el comando: id de la reserva, huésped, id de la unidad y fechas. El nombre de la unidad no viaja en el mensaje (vive en catalog), así que se muestra el id.

## API REST

Base path: `/api/notifications`. A través del BFF, cualquier usuario autenticado tiene acceso, y cada uno ve solo lo suyo. La identidad llega en los headers que agrega el BFF: `X-User-Id` (el `oid` del token) y `X-User-Roles`.

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/notifications` | Últimos 50 avisos visibles, del más reciente al más antiguo |
| `PATCH` | `/api/notifications/{id}/read` | Marca un aviso como leído. **404** si no existe o no es visible para el usuario |
| `PATCH` | `/api/notifications/read-all` | Marca como leídos todos los avisos visibles. Responde `{"updated": n}` |

**Qué ve cada usuario:**
- **Cliente:** los avisos `GUEST` cuyo `recipientId` es su `oid`.
- **Recepcionista / Admin:** además, todos los avisos `STAFF`. La bandeja del personal es compartida: si un recepcionista marca un aviso como leído, queda leído para todos.

```json
GET /api/notifications
[
  {
    "id": 12,
    "audience": "GUEST",
    "type": "notification.confirmed",
    "title": "Reserva confirmada",
    "message": "Tu reserva #7 en la unidad 3 del 01-12-2026 al 05-12-2026 fue confirmada. ¡Te esperamos!",
    "reservationId": 7,
    "unitId": 3,
    "read": false,
    "createdAt": "2026-10-10T15:20:11Z"
  }
]
```

## Modelo

Tabla `notifications`:

| Campo | Descripción |
|---|---|
| `eventId` | `eventId` del comando. **Único:** una reentrega de RabbitMQ no duplica el aviso |
| `audience` | `GUEST` o `STAFF` |
| `recipient_id` | `oid` del huésped si es `GUEST`; `null` si es `STAFF` |
| `type` | Routing key que lo originó, por ejemplo `housekeeping.prepare_unit` |
| `title`, `message` | Texto del aviso |
| `reservationId`, `unitId` | Referencias lógicas, sin FK |
| `is_read` | Si ya se leyó |
| `createdAt` | Cuándo se generó |

> El aviso al huésped solo sirve si la reserva se creó con `guestId` = `oid` del usuario, que es lo que manda el front. Las reservas del seed (`u-100`, `guest-123`...) no le llegan a nadie. Si el comando no trae `guestId`, se descarta con un warning.

## Procesamiento de mensajes (`NotificationListener`)

Hay un `@RabbitListener` por cola, con ACK manual:

1. Si el `eventId` ya se procesó en esta ejecución (`ProcessedEventStore`, en memoria), hace ACK y lo ignora.
2. Si no, ejecuta la acción. Al guardar, el service vuelve a comprobar el `eventId` en la base, así que la idempotencia se mantiene aunque notify se reinicie.
3. Si sale bien, hace **ACK**.
4. Si falla, hace **NACK sin requeue** y el mensaje cae en su DLQ (`q.cmd.*.dlq`) vía `cmd.dead.dlx`.

No hay reintentos: un fallo manda el mensaje directo a la DLQ.

## Topología RabbitMQ

notify re-declara (`RabbitConfig`) la misma topología que define reservations. La declaración es idempotente mientras los nombres y argumentos coincidan, así que cualquiera de los dos puede arrancar primero.

```
cmd.topic ──notification.*──▶ q.cmd.notification ─┐
          ──housekeeping.#──▶ q.cmd.housekeeping ──┤  NACK
          ──voucher.*───────▶ q.cmd.voucher ───────┤
cmd.direct (notification.send · housekeeping.ticket · voucher.gen, mismas colas)
                                                   ▼
               cmd.dead.dlx ──▶ q.cmd.notification.dlq · q.cmd.housekeeping.dlq · q.cmd.voucher.dlq
```

> La cola de avisos al huésped antes se llamaba `q.cmd.email`. Tras el deploy hay que borrar `q.cmd.email` y `q.cmd.email.dlq` a mano en la Management UI. Ver [ec2-mq](../../ec2-mq/README.md).

## Estructura

```
ms_andesstay_notify/
├── config/       RabbitConfig (topología + JSON converter) · RabbitConstants
├── messaging/    CommandEnvelope · NotificationPayload · ProcessedEventStore
├── listener/     NotificationListener (un @RabbitListener por cola, ACK/NACK manual)
├── domain/       Notification · Audience · NotificationNotFoundException
├── repository/   NotificationRepository
├── dto/          NotificationResponse
├── service/      NotificationService (textos de los avisos + consultas)
└── controller/   NotificationController · GlobalExceptionHandler
```

## Configuración

`src/main/resources/application.yml`; en el contenedor se sobrescribe desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8083` | — |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/notify_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.password` | `andesstay` | `SPRING_DATASOURCE_PASSWORD` |
| `spring.rabbitmq.host` / `port` | `localhost:5672` | `SPRING_RABBITMQ_HOST` / `_PORT` |
| `spring.rabbitmq.username` / `password` | `andesstay` / `andesstay` | `SPRING_RABBITMQ_USERNAME` / `_PASSWORD` |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `manual` | — |
| `...listener.simple.concurrency` / `max-concurrency` | `1` / `3` | — |

`ddl-auto: update` crea la tabla `notifications` en el primer arranque. La base `notify_db` la crea [`postgres-init`](../postgres-init/README.md); si el volumen de Postgres ya existía, hay que crearla a mano.

## Ejecutar localmente

Requiere PostgreSQL (`notify_db`) y RabbitMQ en `localhost:5672`, con el usuario `andesstay`.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8083/actuator/health`.
