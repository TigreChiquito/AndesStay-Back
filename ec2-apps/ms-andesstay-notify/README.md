# ms-andesstay-notify

Microservicio de **avisos in-app y vouchers**. Consume los comandos que `ms-andesstay-reservations` publica en RabbitMQ. Con ellos guarda notificaciones en su propia base (`notify_db`), que la campanita del front consulta por API, y genera el **voucher en PDF** de cada reserva confirmada.

## Las 3 colas

| Cola | Para quién | Qué hace |
|---|---|---|
| `q.cmd.notification` | **Huésped** (Cliente) | Guarda un aviso para el dueño de la reserva (`recipientId = guestId`) |
| `q.cmd.housekeeping` | **Personal** (Recepcionista y Admin) | Guarda un aviso en la bandeja compartida del personal |
| `q.cmd.voucher` | **Huésped** (y el personal) | Genera el voucher en PDF de la reserva confirmada y lo guarda para descargarlo |

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

| Método | Endpoint | Para qué sirve | Acceso (vía BFF) | Respuestas |
|---|---|---|---|---|
| `GET` | `/api/notifications` | Listar los avisos del usuario (los últimos 50, del más reciente al más antiguo). La campanita del front lo consulta cada 30 s | Autenticado (cada uno ve los suyos) | 200 |
| `PATCH` | `/api/notifications/{id}/read` | Marcar un aviso como leído, al hacer clic en él | Autenticado | 200; 404 si no existe o no es visible para el usuario |
| `PATCH` | `/api/notifications/read-all` | Marcar como leídos todos los avisos visibles ("Marcar todas como leídas") | Autenticado | 200 `{"updated": n}` |
| `GET` | `/api/vouchers/{reservationId}` | Descargar el voucher en PDF de una reserva confirmada | Autenticado (el huésped, los suyos; Recepcionista/Admin, cualquiera) | 200 `application/pdf` con `Content-Disposition: attachment; filename="voucher-AS-000007.pdf"`; 404 si no existe o no es visible |
| `GET` | `/actuator/health` | Indica si el micro está arriba; lo usan los healthchecks | Interno (red de Docker) | 200 `{"status":"UP"}` |

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

## Voucher

Cuando una reserva pasa a **CONFIRMADA**, reservations publica `voucher.gen` y notify (`VoucherService`):

1. Si la reserva ya tiene voucher, no hace nada (idempotente por `reservationId`).
2. Le pide a catalog (`GET /api/units/{id}`) el nombre, la ubicación y la tarifa de la unidad. Es *best effort*: timeout de 3 s, y si catalog no responde, el voucher se emite igual, solo con el número de unidad y sin montos.
3. Calcula las noches y el total (tarifa × noches).
4. Genera el PDF con **OpenPDF 2.0.5** (`VoucherPdfGenerator`) y lo guarda en la tabla `vouchers`, como bytes.

El PDF incluye el código (`AS-` + id de la reserva con 6 dígitos, por ejemplo `AS-000007`), la fecha de emisión, el huésped, el número de reserva, la unidad y su ubicación, el check-in, el check-out, las noches, la tarifa por noche y el total.

> **OpenPDF 2.0.5** es la última versión compilada para Java 17. Desde la 2.1.0 la librería requiere Java 21, y fallaría al arrancar con `UnsupportedClassVersionError`. No la actualices sin pasar antes el proyecto a Java 21.

Como el PDF queda guardado, descargarlo no depende de catalog. Se conserva aunque la reserva se cancele después.

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

Tabla `vouchers`:

| Campo | Descripción |
|---|---|
| `reservationId` | Reserva a la que pertenece. **Único**: un voucher por reserva |
| `code` | Código legible, por ejemplo `AS-000007` (único) |
| `guestId` | `oid` del huésped: define quién puede descargarlo |
| `guestName`, `unitId`, `checkInDate`, `checkOutDate` | Datos de la reserva al momento de emitirlo |
| `total` | Total de la estadía; `null` si catalog no respondió |
| `pdf` | El documento (`bytea`, unos 2 KB) |
| `createdAt` | Cuándo se emitió |

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

> La cola de avisos al huésped antes se llamaba `q.cmd.email`. En el despliegue actual las colas viejas ya se borraron; para otro broker, ver la migración en [ec2-mq](../../ec2-mq/README.md).

## Estructura

```
ms_andesstay_notify/
├── config/       RabbitConfig (topología + JSON converter) · RabbitConstants
├── messaging/    CommandEnvelope · NotificationPayload · ProcessedEventStore
├── listener/     NotificationListener (un @RabbitListener por cola, ACK/NACK manual)
├── client/       CatalogClient · UnitInfo (datos de la unidad para el voucher)
├── domain/       Notification · Audience · Voucher · *NotFoundException
├── repository/   NotificationRepository · VoucherRepository
├── dto/          NotificationResponse
├── service/      NotificationService (avisos) · VoucherService · VoucherPdfGenerator (PDF)
└── controller/   NotificationController · VoucherController · RequestUser · GlobalExceptionHandler
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
| `catalog.base-url` | `http://localhost:8082` | `CATALOG_BASE_URL` |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `manual` | — |
| `...listener.simple.concurrency` / `max-concurrency` | `1` / `3` | — |

`ddl-auto: update` crea las tablas `notifications` y `vouchers` en el primer arranque. La base `notify_db` la crea [`postgres-init`](../postgres-init/README.md); si el volumen de Postgres ya existía, hay que crearla a mano.

## Ejecutar localmente

Requiere PostgreSQL (`notify_db`) y RabbitMQ en `localhost:5672`, con el usuario `andesstay`. catalog es opcional: sin él, los vouchers salen sin nombre de unidad ni montos.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8083/actuator/health`.
