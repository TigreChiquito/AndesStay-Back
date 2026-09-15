# AndesStay — Backend

Backend de **AndesStay**, una plataforma de reservas de hospedaje (hostales, cabañas y lodges), construido como un conjunto de microservicios independientes en Spring Boot.

## Arquitectura

El sistema sigue un estilo orientado a eventos: `ms-andesstay-reservations` es el origen de los eventos de negocio, que se propagan de forma asíncrona hacia los servicios de notificación, reportería y auditoría. La comunicación síncrona (REST) se usa solo cuando se necesita una respuesta inmediata (por ejemplo, descontar disponibilidad en el catálogo).

```
                         REST                         REST
   Cliente/BFF  ───────────────────▶  ms-andesstay-reservations ────────▶ ms-andesstay-catalog
                                          │        │
                              Kafka:      │        │  RabbitMQ (cmd.direct / cmd.topic)
                        reservations.events        │  email.* · housekeeping.# · voucher.*
                              │        │            │
                ┌─────────────┘        └────────────┘
                ▼                                   ▼
   ms-andesstay-report                    ms-andesstay-notify
   ms-andesstay-audit                     (email, housekeeping, voucher)
```

- **Kafka** (`reservations.events`): fuente de verdad de todo lo que le pasa a una reserva (creación y cambios de estado). La consumen `ms-andesstay-report` y `ms-andesstay-audit` para construir sus propios *read models*, cada uno con su `group-id` (fan-out).
- **RabbitMQ** (`cmd.direct` / `cmd.topic` + DLX): comandos puntuales que reservations dispara ante ciertos cambios de estado (confirmar envía email + voucher, check-in pendiente avisa a housekeeping, etc.). Los consume `ms-andesstay-notify`, con colas de dead-letter (DLQ) para mensajes que fallan tras reintentar.
- **REST**: reservations llama a catalog (`/api/units/{id}/reserve` y `/release`) para descontar o devolver cupo al confirmar o cancelar una reserva.

## Microservicios

| Servicio | Puerto | Responsabilidad | README |
|---|---|---|---|
| `ms-andesstay-reservations` | 8081 | Ciclo de vida de la reserva (máquina de estados), publica eventos Kafka y comandos RabbitMQ | [ver](ec2-apps/ms-andesstay-reservations/README.md) |
| `ms-andesstay-catalog` | 8082 | CRUD de unidades de hospedaje y control de disponibilidad (cupos) | [ver](ec2-apps/ms-andesstay-catalog/README.md) |
| `ms-andesstay-notify` | 8083 | Consumidor de comandos RabbitMQ: emails, tickets de housekeeping y vouchers | [ver](ec2-apps/ms-andesstay-notify/README.md) |
| `ms-andesstay-report` | 8080 (por defecto) | Read model de reportería (KPIs, conteos por estado/unidad) alimentado por Kafka | [ver](ec2-apps/ms-andesstay-report/README.md) |
| `ms-andesstay-audit` | 8085 | Read model de auditoría (línea de tiempo por reserva) alimentado por Kafka | [ver](ec2-apps/ms-andesstay-audit/README.md) |

Todos los servicios son proyectos Maven independientes (sin módulo padre común), pensados para desplegarse por separado — de ahí el nombre de la carpeta que los agrupa, [`ec2-apps/`](ec2-apps/README.md).

## Stack técnico

- Java 17
- Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL (reservations, catalog, audit, report)
- Apache Kafka (eventos de dominio)
- RabbitMQ (comandos asíncronos, con topología direct + topic + dead-lettering)
- Maven Wrapper (`./mvnw`) en cada servicio

## Requisitos previos

- JDK 17+
- PostgreSQL corriendo localmente (una base de datos por servicio que la necesite: `reservations_db`, `catalog_db`, `audit_db`)
- Kafka corriendo en `localhost:9092`
- RabbitMQ corriendo en `localhost:5672`

Usuario/contraseña por defecto en la configuración de desarrollo: `andesstay` / `andesstay` (sobrescribibles con las variables de entorno `DB_PASSWORD` y `RABBIT_PASSWORD`).

## Cómo levantar un servicio

Cada microservicio se ejecuta de forma independiente desde su propio directorio:

```bash
cd ec2-apps/ms-andesstay-reservations
./mvnw spring-boot:run
```

Repite lo mismo para los demás servicios que necesites (ver el README de cada uno para sus dependencias específicas de infraestructura). Todos exponen `/actuator/health` para verificar que están arriba.

## Estructura del repositorio

```
AndesStay-Back/
└── ec2-apps/
    ├── ms-andesstay-reservations/
    ├── ms-andesstay-catalog/
    ├── ms-andesstay-notify/
    ├── ms-andesstay-report/
    └── ms-andesstay-audit/
```
