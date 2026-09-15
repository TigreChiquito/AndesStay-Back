# ms-andesstay-notify

Microservicio consumidor de **comandos de notificación** publicados por `ms-andesstay-reservations` en RabbitMQ: envío de emails, generación de tickets de housekeeping y generación de vouchers.

## Responsabilidades

- Consumir las tres colas de comandos (`q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher`) y ejecutar la acción correspondiente.
- Garantizar idempotencia: si el mismo `eventId` llega más de una vez (reentrega), se ignora sin reprocesar.
- Confirmar (`ACK`) explícitamente cada mensaje procesado con éxito, o rechazarlo (`NACK` sin *requeue*) si falla, para que la cola lo enrute a su dead-letter queue (DLQ).

Este servicio **no produce** eventos de negocio; es puramente consumidor.

## Topología RabbitMQ

`ms-andesstay-notify` re-declara (`RabbitConfig`) la misma topología que define `ms-andesstay-reservations` — la declaración es idempotente mientras los nombres coincidan exactamente, así que este servicio puede arrancar incluso antes que el productor:

- Exchanges: `cmd.direct` (routing key exacta) y `cmd.topic` (patrones `email.*`, `housekeeping.#`, `voucher.*`), con dead-lettering hacia `cmd.dead.dlx`.
- Colas: `q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher`, cada una con su DLQ (`q.cmd.email.dlq`, etc.).

## Flujo de procesamiento (`NotificationListener`)

Cada listener sigue el mismo patrón:

1. Si el `eventId` del `CommandEnvelope` ya fue procesado antes → `ACK` y se ignora (deduplicación).
2. Si no, ejecuta la acción (`sendEmail`, `createHousekeepingTicket`, `generateVoucher`).
3. Si tiene éxito, marca el evento como procesado y hace `ACK`.
4. Si falla, hace `NACK` sin *requeue* → el mensaje cae en su DLQ vía `cmd.dead.dlx`.

El modo de acknowledge es manual (`spring.rabbitmq.listener.simple.acknowledge-mode: manual`), necesario para poder controlar el ACK/NACK a mano en el código.

## Configuración

Ver `src/main/resources/Application.yml`.

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `server.port` | `8083` | Puerto HTTP |
| `spring.rabbitmq.host` / `port` | `localhost:5672` | Broker RabbitMQ |
| `spring.rabbitmq.password` | `andesstay` | Sobrescribible con `RABBIT_PASSWORD` |
| `spring.rabbitmq.listener.simple.concurrency` | `1` (hasta `3` con `max-concurrency`) | Consumidores por cola |

Este servicio no tiene base de datos propia; el estado de idempotencia se mantiene en memoria (`ProcessedEventStore`).

## Ejecutar localmente

Requiere RabbitMQ corriendo en `localhost` (y, para ver mensajes fluir, `ms-andesstay-reservations` publicando comandos).

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8083/actuator/health`.

## Tests

```bash
./mvnw test
```
