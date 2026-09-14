# AndesStay — Contexto del Backend (handoff de sesión)

Plataforma de reservas de hospedaje basada en microservicios (Caso 5). Este
documento resume el estado del **backend en Spring Boot**, las decisiones
tomadas y lo que falta. El **frontend (Angular + MSAL)** lo trabaja la compañera
y no se cubre acá.

---

## 1. Stack y decisiones base

| Aspecto | Decisión |
|---|---|
| Framework | Spring Boot **4.1.1** (misma versión en TODOS los micros) |
| Lenguaje | Java **17** |
| Build | Maven (con wrapper `mvnw`) |
| Group | `cl.tigrechiquito` |
| Base de datos | **PostgreSQL** (desvío aprobado por el profe; la pauta decía Oracle) |
| Mensajería | RabbitMQ (comandos) + Kafka en KRaft para dev (eventos) |
| Repositorio | **Monorepo** agrupado por máquina EC2 |
| Convención de capas | carpeta `controller/` (en vez de `web/`) |

### Desvíos respecto a la pauta (documentar en la entrega)
- **Oracle → PostgreSQL** (autorizado por el profe).
- **Monorepo** en vez de un repo por microservicio (la pauta sugería repos separados).
- Nombre de paquete generado por Initializr con guiones bajos: `cl.tigrechiquito.ms_andesstay_<nombre>`.

---

## 2. Estructura del monorepo

```
AndesStay-Back/
├── .github/workflows/        # workflows GitHub Actions (con paths: por carpeta) — PENDIENTE
├── dev/                      # infra SOLO para desarrollo local
│   ├── compose.yml           # Postgres + RabbitMQ + Kafka + Kafka UI
│   └── init/
│       └── 01-create-databases.sql
├── ec2-apps/                 # MÁQUINA 1: microservicios Spring Boot + Postgres
│   ├── ms-andesstay-reservations/   ✅
│   ├── ms-andesstay-catalog/        ✅
│   ├── ms-andesstay-notify/         ✅
│   ├── ms-andesstay-report/         ✅
│   ├── ms-andesstay-audit/          ✅
│   └── ms-andesstay-bff/            ⬜ PENDIENTE
├── ec2-mq/                   # MÁQUINA 2: RabbitMQ (imágenes oficiales) — compose PENDIENTE
└── ec2-kafka/               # MÁQUINA 3: Zookeeper + Kafka (imágenes oficiales) — compose PENDIENTE
```

Cada microservicio es **autocontenido** (su `pom.xml`, `mvnw`, `Dockerfile` propio,
`src/`). Los Dockerfiles y los `compose.yml` de deploy son de la fase final.

### Las 3 máquinas EC2 (sección 7 de la pauta)
- **ec2-apps**: los 6 micros Spring Boot + **1 contenedor Postgres con una base por micro**
  (`reservations_db`, `catalog_db`, `audit_db`, `report_db`). Patrón *database-per-service*
  (un motor, varias bases). notify y bff no tienen base.
- **ec2-mq**: RabbitMQ (clúster 2 nodos) + Management UI.
- **ec2-kafka**: Zookeeper (3) + Kafka (3 brokers) + Kafka UI.

---

## 3. Estado de los microservicios

| Micro | Puerto | Base | Rol | Estado |
|---|---|---|---|---|
| reservations | 8081 | reservations_db | REST reservas + máquina de estados + productor Rabbit y Kafka | ✅ |
| catalog | 8082 | catalog_db | REST unidades/cupos/tarifas + disponibilidad | ✅ |
| notify | 8083 | — | Consumidor RabbitMQ (email/voucher/housekeeping) | ✅ |
| report | 8084 | report_db | Consumidor Kafka → KPIs (read model / CQRS) | ✅ |
| audit | 8085 | audit_db | Consumidor Kafka → línea de tiempo (append-only) | ✅ |
| bff | — | — | Seguridad Azure AD (JWT) + enrutado | ⬜ |

---

## 4. Detalle por microservicio

### 4.1 reservations (8081)
Corazón del sistema. Máquina de estados de la reserva y origen de toda la mensajería.

**Máquina de estados:**
`CREADA → CONFIRMADA → CHECKIN_PENDIENTE → EN_ESTADIA → CHECKOUT`, con `CANCELADA`
como terminal alcanzable antes de iniciar la estadía. Regla "no check-in sin
confirmar" garantizada por la tabla de transiciones.

**Endpoints:**
- `POST /api/reservations` — crea (nace CREADA), 201
- `GET /api/reservations/{id}` — 404 si no existe
- `PUT /api/reservations/{id}/status` — cambia estado; **409** si la transición es inválida
- `GET /api/reservations?status=&from=&to=` — filtros opcionales

**Estructura:**
```
ms_andesstay_reservations/
├── domain/        Reservation · ReservationStatus · InvalidReservationStatusTransitionException · ReservationNotFoundException
├── repository/    ReservationRepository
├── dto/           CreateReservationRequest · UpdateStatusRequest · ReservationResponse
├── service/       ReservationService
├── controller/    ReservationController · GlobalExceptionHandler
└── messaging/
    ├── ReservationCreatedEvent           (evento interno compartido)
    ├── ReservationStatusChangedEvent      (evento interno compartido)
    ├── rabbit/   RabbitConstants · RabbitTopologyConfig · CommandEnvelope · ReservationCommand · ReservationCommandPublisher · ReservationEventListener
    └── kafka/    KafkaConstants · ReservationEventMessage · ReservationEventPublisher · KafkaEventListener · KafkaTopicConfig
```
> Los 2 eventos internos quedan en `messaging/` (raíz) porque los usan ambos transportes.

### 4.2 catalog (8082)
CRUD de unidades (hostal/cabaña/lodge) con cupos y tarifas. reservations le baja
disponibilidad vía REST.

**Endpoints:**
- `POST /api/units`, `GET /api/units/{id}`, `GET /api/units?type=&available=`
- `PUT /api/units/{id}`, `DELETE /api/units/{id}`
- `POST /api/units/{id}/reserve` — baja un cupo (**409** si no hay)
- `POST /api/units/{id}/release` — devuelve un cupo

**Estructura:** `domain/` (Unit · UnitType · NoAvailabilityException · UnitNotFoundException) ·
`repository/` · `dto/` · `service/` (CatalogService) · `controller/`

### 4.3 notify (8083, sin base)
Consumidor RabbitMQ. Simula email/voucher/housekeeping con logs.

**Estructura:** `config/` (RabbitConfig · RabbitConstants) · `messaging/` (CommandEnvelope ·
NotificationPayload · ProcessedEventStore) · `service/` (NotificationService) ·
`listener/` (NotificationListener)

- **ACK/NACK manual**, idempotencia por `eventId` (en memoria), NACK sin requeue → DLQ.

### 4.4 report (8084, read model)
Consumidor Kafka. Mantiene una proyección (estado actual por reserva) y expone KPIs.

**Endpoints:** `GET /api/reports/summary` · `GET /api/reports/by-status` · `GET /api/reports/by-unit`

**Estructura:** `domain/` (ReservationProjection) · `repository/` (+ StatusCount · UnitCount) ·
`messaging/` (KafkaConstants · ReservationEventMessage · KafkaConsumerConfig · ReservationEventConsumer) ·
`dto/` · `service/` · `controller/`

- Patrón **CQRS**: upsert por reserva; agregaciones en la base (GROUP BY).

### 4.5 audit (8085, append-only)
Consumidor Kafka. Guarda una fila por evento (historial inmutable).

**Endpoints:** `GET /api/audit/reservations/{id}` (línea de tiempo) · `GET /api/audit/events?limit=50`

**Estructura:** `domain/` (AuditEvent) · `repository/` · `messaging/` (KafkaConstants ·
ReservationEventMessage · KafkaConsumerConfig · AuditEventConsumer) · `dto/` · `service/` · `controller/`

- **Append-only** (nunca update/delete), idempotencia por `eventId` + constraint único.

---

## 5. Topología de mensajería

### 5.1 RabbitMQ (comandos — "haz esto")
- **Exchanges:** `cmd.direct` · `cmd.topic` · `cmd.dead.dlx`
- **Colas:** `q.cmd.email` · `q.cmd.housekeeping` · `q.cmd.voucher` (+ sus `.dlq`)
- **Bindings direct:** `email.send` · `housekeeping.ticket` · `voucher.gen`
- **Bindings topic:** `email.*` · `housekeeping.#` · `voucher.*`
- **Envelope:** `type, eventId, timestamp, traceId, correlationId, payload`
- reservations publica al **topic**; notify consume con ACK/NACK → DLQ.

**Disparadores (según estado):**
- → CONFIRMADA: email confirmación + voucher
- → CHECKIN_PENDIENTE: ticket housekeeping + email recordatorio
- → CHECKOUT: email checkout

### 5.2 Kafka (eventos — "esto pasó")
- **Tópico:** `reservations.events` (3 particiones; réplicas: **1 en dev**, 3 en ec2-kafka)
- **Key** = `reservationId` (ordena eventos por reserva)
- **DLT por consumidor:** `reservations.events.DLT` (report) · `reservations.events.audit.DLT` (audit)
- reservations emite a Kafka en **creación y cada cambio de estado** (fuente de verdad).
- report y audit consumen en **grupos distintos** → ambos reciben todos los eventos (fan-out).

---

## 6. Entorno de desarrollo (`dev/compose.yml`)

Un solo comando levanta todo lo que los micros necesitan en local:
```bash
cd dev && docker compose up -d
```

| Servicio | Imagen | Puertos | Credenciales |
|---|---|---|---|
| postgres | postgres:17 | 5432 | andesstay / andesstay |
| rabbitmq | rabbitmq:3-management | 5672, 15672 (UI) | andesstay / andesstay |
| kafka | apache/kafka:latest (KRaft) | 9092 | — |
| kafka-ui | provectuslabs/kafka-ui | 8090 (UI) | — |

- El `init/01-create-databases.sql` crea `catalog_db`, `audit_db`, `report_db`
  (`reservations_db` la crea `POSTGRES_DB`).
- Solo corre en la **primera** creación del volumen. Para re-crear: `docker compose down -v`.
- Los micros corren desde el IDE y apuntan a `localhost`.

---

## 7. Gotchas de Spring Boot 4 (verificados en la sesión)

Boot 4 (nov-2025) trae cambios que rompen tutoriales de Boot 3:

- **Starters renombrados:** `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**;
  hay un `*-test` por cada starter; Kafka usa `spring-boot-starter-kafka`.
- **OAuth2 (para el bff):** `spring-boot-starter-oauth2-resource-server` →
  **`spring-boot-starter-security-oauth2-resource-server`**.
- **Jackson 3 en RabbitMQ:** `Jackson2JsonMessageConverter` (deprecado) → **`JacksonJsonMessageConverter`**.
  Por defecto infiere el tipo del método del `@RabbitListener` y confía en todos los paquetes.
- **Jackson 3 en Kafka:** `JsonSerializer`/`JsonDeserializer` (deprecados) →
  **`JacksonJsonSerializer`/`JacksonJsonDeserializer`**.
- **Propiedad Kafka:** `spring.json.add.type.headers` (no `add.type.info.headers`).
- **RabbitMQ `guest`:** solo conecta desde localhost del contenedor → se usa usuario propio
  (`andesstay`) para que los micros del host puedan conectarse.

---

## 8. Decisiones de diseño transversales

- **Modelo de dominio rico:** la máquina de estados vive en `Reservation.changeStatusTo()`
  y la disponibilidad en `Unit.reserveOne()/releaseOne()` — nadie las deja inconsistentes.
- **Bloqueo optimista (`@Version`)** en `Unit`: evita overbooking cuando se confirma el
  último cupo en paralelo (→ 409).
- **`BigDecimal`** para la tarifa (nunca `double`).
- **`@JsonCreator` tolerante** a tildes/ñ en los enums (`EN_ESTADÍA` → `EN_ESTADIA`, `CABAÑA` → `CABANA`).
- **Publicación AFTER_COMMIT:** los mensajes salen solo si la transacción hizo commit;
  y si el broker está caído, la operación de BD no se cae.
- **Eventos de dominio internos** → un listener por transporte (Rabbit y Kafka desacoplados del service).
- **CQRS** en report (read model) / **append-only** en audit.
- **Idempotencia** en todos los consumidores (eventId en notify/audit, timestamp en report).
- **DTOs** no exponen las entidades JPA. Errores con **ProblemDetail (RFC 7807)**.

---

## 9. Cómo probar el flujo end-to-end

1. `cd dev && docker compose up -d` (esperar ~20s a Kafka).
2. Correr reservations (8081), catalog (8082), notify (8083), report (8084), audit (8085).
3. Crear unidad en catalog y reserva en reservations; confirmar la reserva.
4. Verificar:
   - **notify** (consola): logs de email/voucher.
   - **RabbitMQ UI** (`localhost:15672`): mensajes en las colas.
   - **Kafka UI** (`localhost:8090`): eventos en `reservations.events`.
   - **report:** `GET localhost:8084/api/reports/summary`.
   - **audit:** `GET localhost:8085/api/audit/reservations/1`.

> Recordar: borrar el `application.properties` vacío de cada micro (dejar solo el `.yml`).

---

## 10. Próximas acciones

1. **bff** (ms-andesstay-bff): seguridad Azure AD (validación JWT, resource server) + enrutado
   hacia los micros. Es el más distinto: sin base ni mensajería.
2. **Cablear REST reservations → catalog:** al CONFIRMAR, llamar `POST /api/units/{id}/reserve`
   (hay un `TODO` marcado en `ReservationService.changeStatus`).
3. **Dockerfiles** por micro + **`compose.yml`** por máquina (ec2-apps con Postgres, ec2-mq, ec2-kafka).
4. **Workflows GitHub Actions** con `paths:` por carpeta (`ec2-apps/ms-andesstay-<x>/**`).
5. **Pruebas** aisladas por micro y luego conjuntas.

---

## 11. Notas y pendientes de la pauta

- **`audit.timeline`:** la pauta pide "quién/qué/cuándo/**desde dónde**". El "desde dónde"
  (IP/origen) no está en `reservations.events` — lo tiene el **bff/gateway**. Definir si ese
  tópico lo produce el bff (recomendado) o audit. Hoy NO está implementado.
- **App Registration "BarrioDigital":** la sección 4 de la pauta lo nombra así (parece
  copy-paste de otro caso; el resto es "andesstay"). Ojo al configurar Azure AD en el bff.
- **BFF no listado en ec2-apps:** la pauta no lo menciona en la lista de esa máquina, pero
  es una app Spring Boot y va ahí (detrás del API Gateway).
- **Nombres de DLT:** report usa `reservations.events.DLT` y audit `reservations.events.audit.DLT`.
  Para simetría, considerar renombrar la de report a `reservations.events.report.DLT`.
- **Réplicas Kafka:** en dev son 1 (un broker); en ec2-kafka deben ser 3.
