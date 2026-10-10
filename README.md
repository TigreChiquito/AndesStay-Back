# AndesStay — Backend

Backend de **AndesStay**, una plataforma de reservas de hospedaje (hostales, cabañas y lodges), construido como un conjunto de microservicios Spring Boot desplegados en **3 máquinas EC2** de AWS.

## Arquitectura

```
  Front (React + MSAL, corre en local)
        │  HTTPS + Bearer token (Azure AD)
        ▼
  AWS API Gateway (HTTP API, resuelve CORS)
        │
        ▼
┌──────────────────────── ec2-apps ─────────────────────────┐
│  ms-andesstay-bff :8080  ← única puerta pública           │
│   valida JWT + roles, enruta /api/<segmento>/**           │
│        │ REST                                             │
│        ├──▶ reservations :8081 ──REST──▶ catalog :8082    │
│        ├──▶ catalog      :8082                            │
│        ├──▶ report       :8084                            │
│        ├──▶ audit        :8085                            │
│        └──▶ notify       :8083 (avisos; consume RabbitMQ) │
│                                                           │
│  Postgres :5432                                           │
└──────────────┬──────────────────────────┬─────────────────┘
               │ AMQP 5672                │ Kafka 9092-9094
               ▼                          ▼
┌──────── ec2-mq ────────┐   ┌──────────── ec2-kafka ────────────┐
│ RabbitMQ (2 nodos)     │   │ Zookeeper ×3 + Kafka ×3 + Kafka UI │
│ avisos → notify        │   │ reservations.events → report/audit │
└────────────────────────┘   └────────────────────────────────────┘
```

El sistema sigue un estilo **orientado a eventos**:

- **REST (síncrono):** solo cuando se necesita una respuesta inmediata. El front habla con el BFF, y reservations le pide a catalog que descuente o devuelva un cupo.
- **Kafka (`reservations.events`)** — *"esto pasó"*: fuente de verdad de todo lo que le ocurre a una reserva (creación y cambios de estado). La consumen report y audit, cada uno con su propio `group-id` (fan-out).
- **RabbitMQ (`cmd.direct` / `cmd.topic` + DLX)** — *"haz esto"*: comandos que reservations dispara al crear una reserva o cambiar su estado: avisos al huésped, avisos al personal (housekeeping) y vouchers. Los consume notify, que guarda los avisos para mostrarlos en la campanita del front, con dead-letter queues para los mensajes que fallan.

## Estructura del repositorio

| Carpeta | Máquina | Contenido | README |
|---|---|---|---|
| [`ec2-apps/`](ec2-apps/README.md) | EC2 #1 | Los 6 microservicios Spring Boot + Postgres | [ver](ec2-apps/README.md) |
| [`ec2-apps/ms-andesstay-bff/`](ec2-apps/ms-andesstay-bff/README.md) | EC2 #1 | Seguridad (Azure AD) y enrutado hacia los micros | [ver](ec2-apps/ms-andesstay-bff/README.md) |
| [`ec2-apps/ms-andesstay-reservations/`](ec2-apps/ms-andesstay-reservations/README.md) | EC2 #1 | Ciclo de vida de la reserva; productor Kafka y RabbitMQ | [ver](ec2-apps/ms-andesstay-reservations/README.md) |
| [`ec2-apps/ms-andesstay-catalog/`](ec2-apps/ms-andesstay-catalog/README.md) | EC2 #1 | Unidades de hospedaje y cupos | [ver](ec2-apps/ms-andesstay-catalog/README.md) |
| [`ec2-apps/ms-andesstay-notify/`](ec2-apps/ms-andesstay-notify/README.md) | EC2 #1 | Consumidor RabbitMQ: avisos in-app al huésped y al personal, voucher | [ver](ec2-apps/ms-andesstay-notify/README.md) |
| [`ec2-apps/ms-andesstay-report/`](ec2-apps/ms-andesstay-report/README.md) | EC2 #1 | Read model de KPIs alimentado por Kafka (CQRS) | [ver](ec2-apps/ms-andesstay-report/README.md) |
| [`ec2-apps/ms-andesstay-audit/`](ec2-apps/ms-andesstay-audit/README.md) | EC2 #1 | Historial append-only alimentado por Kafka | [ver](ec2-apps/ms-andesstay-audit/README.md) |
| [`ec2-apps/postgres-init/`](ec2-apps/postgres-init/README.md) | EC2 #1 | Script que crea las bases al iniciar Postgres | [ver](ec2-apps/postgres-init/README.md) |
| [`ec2-apps/sql/`](ec2-apps/sql/README.md) | EC2 #1 | Datos de prueba (seed) | [ver](ec2-apps/sql/README.md) |
| [`ec2-mq/`](ec2-mq/README.md) | EC2 #2 | Clúster RabbitMQ de 2 nodos | [ver](ec2-mq/README.md) |
| [`ec2-kafka/`](ec2-kafka/README.md) | EC2 #3 | Zookeeper ×3 + Kafka ×3 + Kafka UI | [ver](ec2-kafka/README.md) |

[`CONTEXTO-AndesStay-Back.md`](CONTEXTO-AndesStay-Back.md) es el documento de traspaso entre sesiones de desarrollo, con las decisiones de diseño y los *gotchas* de Spring Boot 4.

## Microservicios

| Servicio | Puerto | Base de datos | Rol |
|---|---|---|---|
| `ms-andesstay-bff` | 8080 (público) | — | Resource server JWT + gateway hacia los micros |
| `ms-andesstay-reservations` | 8081 | `reservations_db` | Máquina de estados; publica a Kafka y RabbitMQ |
| `ms-andesstay-catalog` | 8082 | `catalog_db` | CRUD de unidades y control de cupos |
| `ms-andesstay-notify` | 8083 | `notify_db` | Consumidor RabbitMQ → avisos in-app (ACK/NACK manual + DLQ) |
| `ms-andesstay-report` | 8084 | `report_db` | Consumidor Kafka → KPIs |
| `ms-andesstay-audit` | 8085 | `audit_db` | Consumidor Kafka → línea de tiempo |

Dentro de ec2-apps solo el BFF publica su puerto (8080). Los demás se comunican por la red interna de Docker usando el nombre del servicio (`http://reservations:8081`, etc.).

## Stack técnico

- Java 17 · Spring Boot **4.1.1** (Web MVC, Data JPA, Validation, Actuator, Security OAuth2 Resource Server)
- PostgreSQL 17 (una base por micro, un solo motor: *database-per-service*)
- Apache Kafka 7.6 (Confluent, con Zookeeper) · RabbitMQ 3 con Management UI
- Docker Compose, uno por máquina · Maven Wrapper (`./mvnw`) en cada micro
- Azure AD (Entra ID) para la autenticación, con App Roles: `Admin`, `Recepcionista`, `Cliente`, `Auditor`

## Despliegue

Cada máquina tiene su propio `compose.yml` y un `.env.example`. Los `.env` reales **no se suben** (están en `.gitignore`).

Orden recomendado:

1. **ec2-kafka**: `cp .env.example .env` (con la IP privada de la máquina) y `docker compose up -d`. Ver [ec2-kafka](ec2-kafka/README.md).
2. **ec2-mq**: `cp .env.example .env` y `docker compose up -d`. Ver [ec2-mq](ec2-mq/README.md).
3. **ec2-apps**: `cp .env.example .env` (con las IPs de las otras dos máquinas y Azure AD) y `docker compose up -d --build`. Ver [ec2-apps](ec2-apps/README.md).

Los Security Groups deben permitir, **desde ec2-apps**, los puertos 5672 hacia ec2-mq y 9092-9094 hacia ec2-kafka. Hacia ec2-apps, solo el 8080 desde el API Gateway.

## Desarrollo local

Cada micro trae en su `application.yml` valores apuntando a `localhost`, de modo que se puede correr desde el IDE contra una infraestructura local (Postgres, Kafka y RabbitMQ con usuario `andesstay` / `andesstay`):

```bash
cd ec2-apps/ms-andesstay-reservations
./mvnw spring-boot:run
```

En los contenedores, esos valores se sobrescriben con variables de entorno (`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, etc.) definidas en cada `compose.yml`. Todos los micros exponen `/actuator/health`.
