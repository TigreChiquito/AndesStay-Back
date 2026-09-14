# ms-andesstay-reservations

Microservicio dueño del ciclo de vida de una **reserva** de hospedaje. Expone la API REST que usa el resto del sistema (o un BFF) para crear y hacer avanzar reservas, y es la fuente de verdad que dispara los eventos y comandos asíncronos consumidos por los demás microservicios de AndesStay.

## Responsabilidades

- CRUD/consulta de reservas y transición de estados según una máquina de estados explícita.
- Publicar en **Kafka** (`reservations.events`) cada creación y cambio de estado, para alimentar `ms-andesstay-report` y `ms-andesstay-audit`.
- Publicar en **RabbitMQ** los comandos de notificación que correspondan a cada cambio de estado, para que `ms-andesstay-notify` los procese.
- Llamar a `ms-andesstay-catalog` por REST para descontar (`/reserve`) o devolver (`/release`) un cupo de la unidad reservada.

## Máquina de estados

```
CREADA ──▶ CONFIRMADA ──▶ CHECKIN_PENDIENTE ──▶ EN_ESTADIA ──▶ CHECKOUT
   │            │                │
   └────────────┴────────────────┴──▶ CANCELADA
```

- `CANCELADA` es un estado terminal alternativo, solo alcanzable antes de que comience la estadía (desde `CREADA`, `CONFIRMADA` o `CHECKIN_PENDIENTE`).
- `CHECKOUT` y `CANCELADA` son terminales.
- El parseo de estados desde JSON es tolerante a tildes y mayúsculas/minúsculas (p. ej. acepta `"en_estadía"`).

La validación de transiciones vive en la propia entidad `Reservation` (modelo de dominio rico), no en el service.

## API REST

Base path: `/api/reservations`

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/reservations` | Crea una reserva (nace en `CREADA`) |
| `GET` | `/api/reservations/{id}` | Obtiene una reserva por id |
| `PUT` | `/api/reservations/{id}/status` | Cambia el estado según la máquina de estados |
| `GET` | `/api/reservations?status=&from=&to=` | Búsqueda con filtros opcionales (estado, rango de fechas de check-in/out) |

Ejemplo de creación:

```json
POST /api/reservations
{
  "guestId": "guest-123",
  "guestName": "Ana Pérez",
  "unitId": 1,
  "checkInDate": "2025-12-01",
  "checkOutDate": "2025-12-05"
}
```

Cambio de estado:

```json
PUT /api/reservations/1/status
{ "status": "CONFIRMADA" }
```

> `guestId` viaja hoy en el body a modo temporal; cuando se integre autenticación (Azure AD), se extraerá del token en vez de recibirse explícito.

## Mensajería

### Kafka — `reservations.events`

Se publica **después del commit** de la transacción (`AFTER_COMMIT`), tanto en la creación como en cada cambio de estado. Es el tópico que alimenta a `ms-andesstay-report` y `ms-andesstay-audit` (cada uno con su propio `group-id`, en modo fan-out).

Tipos de evento: `reservation.created`, `reservation.status_changed`.

### RabbitMQ — comandos de notificación

Este servicio declara y es dueño de la topología de comandos (`RabbitTopologyConfig`), que `ms-andesstay-notify` re-declara de forma idempotente al arrancar:

- Exchanges: `cmd.direct` (routing key exacta) y `cmd.topic` (patrones), ambos con dead-lettering hacia `cmd.dead.dlx`.
- Colas principales: `q.cmd.email`, `q.cmd.housekeeping`, `q.cmd.voucher`, cada una con su DLQ (`*.dlq`).

Ante un cambio de estado (también `AFTER_COMMIT`, para no notificar algo que luego hace rollback) se publican estos comandos:

| Estado nuevo | Comandos publicados |
|---|---|
| `CONFIRMADA` | `email.confirmation`, `voucher.gen` |
| `CHECKIN_PENDIENTE` | `housekeeping.ticket`, `email.reminder` |
| `CHECKOUT` | `email.checkout` |
| `CREADA` / `EN_ESTADIA` / `CANCELADA` | (sin notificación) |

## Integración con catalog

Al confirmar una reserva se llama a `POST /api/units/{id}/reserve` en `ms-andesstay-catalog` para descontar un cupo; al cancelarla, a `POST /api/units/{id}/release` para devolverlo. No hay FK entre servicios: `unitId` es solo una referencia lógica a otro microservicio.

## Configuración

Ver `src/main/resources/Application.yml`.

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `server.port` | `8081` | Puerto HTTP |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/reservations_db` | Base de datos propia |
| `spring.datasource.password` | `andesstay` | Sobrescribible con `DB_PASSWORD` |
| `spring.rabbitmq.*` | `localhost:5672` | Sobrescribible con `RABBIT_PASSWORD` |

`spring.jpa.hibernate.ddl-auto=update` está pensado solo para desarrollo.

## Ejecutar localmente

Requiere PostgreSQL (`reservations_db`), Kafka y RabbitMQ corriendo en `localhost`.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8081/actuator/health`.

## Tests

```bash
./mvnw test
```
