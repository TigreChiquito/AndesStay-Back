# ms-andesstay-notify

Microservicio **consumidor de comandos** de RabbitMQ publicados por `ms-andesstay-reservations`: emails al huésped, vouchers y tickets de housekeeping.

Es puramente consumidor: no tiene base de datos, no expone API de negocio y no produce eventos. Las acciones están **simuladas con logs**: no envía emails ni genera PDFs reales.

## Responsabilidades

- Consumir las colas `q.cmd.email`, `q.cmd.housekeeping` y `q.cmd.voucher`, y ejecutar la acción correspondiente.
- **Idempotencia:** si llega dos veces el mismo `eventId` (reentrega), se ignora sin reprocesar.
- **ACK/NACK manual:** confirma cada mensaje procesado con éxito, o lo rechaza sin *requeue* para que caiga en su DLQ.

## Qué dispara cada comando

| Cola | Routing keys (vía `cmd.topic`) | Acción (`NotificationService`) | Lo origina |
|---|---|---|---|
| `q.cmd.email` | `email.*` (`email.confirmation`, `email.reminder`, `email.checkout`) | `sendEmail`: log con huésped y fechas | → CONFIRMADA, → CHECKIN_PENDIENTE, → CHECKOUT |
| `q.cmd.voucher` | `voucher.*` (`voucher.gen`) | `generateVoucher`: log "generando PDF" | → CONFIRMADA |
| `q.cmd.housekeeping` | `housekeeping.#` (`housekeeping.ticket`) | `createHousekeepingTicket`: log con unidad y check-in | → CHECKIN_PENDIENTE |

Para verlo funcionar, sigue el log del contenedor:

```bash
docker logs -f andesstay-notify
```

## Topología RabbitMQ

notify re-declara (`RabbitConfig`) la misma topología que define reservations. La declaración es idempotente mientras los nombres y argumentos coincidan exactamente, así que cualquiera de los dos servicios puede arrancar primero.

```
cmd.topic ──email.*──────────▶ q.cmd.email ────────┐
          ──housekeeping.#───▶ q.cmd.housekeeping ─┤  NACK / rechazo
          ──voucher.*────────▶ q.cmd.voucher ──────┤
cmd.direct (email.send · housekeeping.ticket · voucher.gen, mismas colas)
                                                   ▼
                      cmd.dead.dlx ──▶ q.cmd.email.dlq · q.cmd.housekeeping.dlq · q.cmd.voucher.dlq
```

## Flujo de procesamiento (`NotificationListener`)

1. Si el `eventId` del `CommandEnvelope` ya fue procesado → `ACK` y se ignora.
2. Si no, ejecuta la acción.
3. Si tiene éxito → marca el `eventId` como procesado y hace `ACK`.
4. Si falla → `NACK` sin *requeue*, y el mensaje cae en su DLQ vía `cmd.dead.dlx`.

Mensaje recibido (`CommandEnvelope<NotificationPayload>`):

```json
{
  "type": "email.confirmation",
  "eventId": "uuid",
  "timestamp": "2026-10-05T22:43:01Z",
  "traceId": "uuid",
  "correlationId": "reservation-1",
  "payload": {
    "reservationId": 1, "guestId": "guest-123", "guestName": "Ana Pérez",
    "unitId": 1, "checkInDate": "2026-12-01", "checkOutDate": "2026-12-05",
    "status": "CONFIRMADA"
  }
}
```

> La idempotencia vive **en memoria** (`ProcessedEventStore`, un `Set` concurrente): se pierde al reiniciar el contenedor y no se comparte entre réplicas. Para producción debería persistirse (en Redis o en una tabla).

## Estructura

```
ms_andesstay_notify/
├── config/      RabbitConfig (topología + JSON converter) · RabbitConstants
├── messaging/   CommandEnvelope · NotificationPayload · ProcessedEventStore
├── service/     NotificationService
└── listener/    NotificationListener (un @RabbitListener por cola, ACK/NACK manual)
```

## Configuración

`src/main/resources/application.yml`; en el contenedor se sobrescribe desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8083` | — |
| `spring.rabbitmq.host` / `port` | `localhost:5672` | `SPRING_RABBITMQ_HOST` / `_PORT` |
| `spring.rabbitmq.username` / `password` | `andesstay` / `andesstay` | `SPRING_RABBITMQ_USERNAME` / `_PASSWORD` |
| `spring.rabbitmq.listener.simple.acknowledge-mode` | `manual` | — |
| `...listener.simple.concurrency` / `max-concurrency` | `1` / `3` | — |

## Ejecutar localmente

Requiere RabbitMQ en `localhost:5672` con el usuario `andesstay`. El usuario `guest` solo conecta desde el localhost del contenedor.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8083/actuator/health`.
