# ec2-apps — Máquina 1: microservicios + Postgres

Contiene los 6 microservicios Spring Boot de AndesStay y un contenedor PostgreSQL con una base por micro (*database-per-service*: un motor, varias bases). Todo se orquesta con [`compose.yml`](compose.yml).

RabbitMQ y Kafka **no** viven aquí: están en [ec2-mq](../ec2-mq/README.md) y [ec2-kafka](../ec2-kafka/README.md), y se referencian por IP mediante el `.env`.

## Contenido

| Carpeta / archivo | Descripción |
|---|---|
| [`ms-andesstay-bff/`](ms-andesstay-bff/README.md) | Puerta de entrada: valida el JWT de Azure AD y enruta hacia los micros |
| [`ms-andesstay-reservations/`](ms-andesstay-reservations/README.md) | Ciclo de vida de la reserva; productor de Kafka y RabbitMQ |
| [`ms-andesstay-catalog/`](ms-andesstay-catalog/README.md) | Unidades de hospedaje y cupos |
| [`ms-andesstay-notify/`](ms-andesstay-notify/README.md) | Avisos in-app (consumidor RabbitMQ + API) |
| [`ms-andesstay-report/`](ms-andesstay-report/README.md) | Read model de KPIs (consumidor Kafka) |
| [`ms-andesstay-audit/`](ms-andesstay-audit/README.md) | Historial de auditoría (consumidor Kafka) |
| [`postgres-init/`](postgres-init/README.md) | SQL que crea las bases al inicializar Postgres |
| [`sql/`](sql/README.md) | Scripts de datos de prueba |
| `compose.yml` | Orquestación de la máquina |
| `.env.example` | Plantilla de variables (copiar a `.env`) |

Cada micro es un proyecto Maven **autocontenido**, sin módulo padre: tiene su propio `pom.xml`, `mvnw` y `Dockerfile`. Los Dockerfiles son multi-etapa: compilan con `eclipse-temurin:17-jdk` y corren con `eclipse-temurin:17-jre`, como el usuario no-root `spring`.

## Servicios del compose

| Servicio | Contenedor | Puerto | Depende de |
|---|---|---|---|
| `postgres` | `andesstay-postgres` | 5432 (interno) | — |
| `reservations` | `andesstay-reservations` | 8081 (interno) | postgres, ec2-mq, ec2-kafka, catalog |
| `catalog` | `andesstay-catalog` | 8082 (interno) | postgres |
| `notify` | `andesstay-notify` | 8083 (interno) | postgres, ec2-mq |
| `report` | `andesstay-report` | 8084 (interno) | postgres, ec2-kafka |
| `audit` | `andesstay-audit` | 8085 (interno) | postgres, ec2-kafka |
| `bff` | `andesstay-bff` | **8080 publicado** | reservations, catalog, report, audit, notify |

Solo el BFF publica un puerto en el host: lo consume el AWS API Gateway. Los demás solo son accesibles dentro de la red de Docker, por nombre de servicio.

## Variables de entorno (`.env`)

```bash
cp .env.example .env
```

| Variable | Ejemplo | Uso |
|---|---|---|
| `DB_USER` | `andesstay` | Usuario de Postgres (lo usan todos los micros con base) |
| `DB_PASSWORD` | — | **Obligatoria.** Contraseña de Postgres |
| `MQ_HOST` | `10.0.x.x` | IP privada de ec2-mq |
| `MQ_PORT` | `5672` | Puerto AMQP |
| `MQ_USER` / `MQ_PASSWORD` | `andesstay` / — | Credenciales de RabbitMQ (deben coincidir con el `.env` de ec2-mq) |
| `KAFKA_BOOTSTRAP` | `10.0.x.x:9092,10.0.x.x:9093,10.0.x.x:9094` | Los 3 brokers de ec2-kafka |
| `AZURE_TENANT_ID` | GUID | Tenant de Azure AD (lo usa el BFF) |
| `AZURE_API_CLIENT_ID` | GUID | Client ID del App Registration de la API (lo usa el BFF) |
| `FRONT_ORIGIN` | `http://localhost:5173` | Origen permitido por el CORS del BFF |

> Las IPs del `.env.example` (`10.0.0.10`, `10.0.0.20`) son de ejemplo: reemplázalas por las IPs privadas reales de ec2-mq y ec2-kafka.

El compose además fija `ANDESSTAY_KAFKA_REPLICAS: 3` en reservations, report y audit. Así sus tópicos se crean con 3 réplicas, que es lo que exige `min.insync.replicas=2` en ec2-kafka.

## Despliegue

```bash
git pull
docker compose up -d --build
```

Para reconstruir un solo micro tras un cambio:

```bash
docker compose up -d --build reservations
```

### Verificación

```bash
docker compose ps
docker logs --tail 100 andesstay-reservations
docker exec andesstay-bff curl -s http://reservations:8081/actuator/health
```

Desde fuera de la máquina, la salud del BFF se ve en `GET <api-gateway>/actuator/health`.

### Primer arranque: datos de prueba

Las tablas las crea Hibernate (`ddl-auto: update`) cuando cada micro arranca por primera vez. Una vez arriba, se pueden cargar los datos de [`sql/`](sql/README.md).

## Problemas frecuentes

| Síntoma | Causa probable | Qué revisar |
|---|---|---|
| El BFF responde **502** en una ruta | El micro de destino está caído o reiniciándose | `docker compose ps` y `docker logs andesstay-<micro>` |
| El API Gateway responde **503** `{"message":"Service Unavailable"}` | El request tardó más de 30 s (timeout del Gateway) | Conectividad con Kafka/RabbitMQ desde ec2-apps |
| **401** con un token válido | Issuer o audience del token no coinciden | `AZURE_TENANT_ID` / `AZURE_API_CLIENT_ID`; el token debe ser v2 |
| **403** | El usuario no tiene el App Role que pide la ruta | Ver la tabla de roles en el [BFF](ms-andesstay-bff/README.md) |
| `No space left on device` al hacer build | Caché de build e imágenes viejas | `docker builder prune -af` y `docker image prune -af` |
| `docker-buildx: exec format error` | El plugin buildx es de otra arquitectura de CPU | Instalar el binario correcto (`uname -m`) |

Para probar si ec2-apps llega a Kafka y a RabbitMQ:

```bash
timeout 3 bash -c '</dev/tcp/<IP_KAFKA>/9092' && echo OK || echo SIN_CONEXION
timeout 3 bash -c '</dev/tcp/<IP_MQ>/5672'   && echo OK || echo SIN_CONEXION
```

⚠️ **No usar** `docker system prune --volumes` ni `docker volume prune`: borrarían el volumen `andesstay_pgdata`, con todos los datos de Postgres.

> Un disco de 8 GB (el default de EC2) queda justo para 6 builds Java + Postgres. Se recomiendan 20-30 GB en el volumen EBS.
