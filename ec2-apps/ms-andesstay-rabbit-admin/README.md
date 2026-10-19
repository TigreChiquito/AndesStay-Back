# ms-andesstay-rabbit-admin

Microservicio **administrador de RabbitMQ**. Permite crear y eliminar colas, exchanges y bindings del clúster por API REST, y expone las **métricas de monitoreo** para la página de administración del front. A través del BFF, solo el rol **Admin** tiene acceso.

No tiene base de datos ni consume mensajes: administra la topología del clúster de [ec2-mq](../../ec2-mq/README.md).

## Cómo funciona

```
BFF (/api/admin/** solo Admin) ──▶ rabbit-admin :8086
                                     │
   crear / eliminar ──AMQP 5672────▶ │ RabbitAdmin  (RabbitAdminService)
   listar / métricas ──HTTP 15672──▶ │ API de la Management UI  (ManagementClient)
                                     ▼
                                  ec2-mq
```

- **Escritura con `RabbitAdmin`** (Spring AMQP), dentro de `RabbitAdminService`.
- **Lectura con la API HTTP de la Management UI.** `RabbitAdmin` solo consulta colas por nombre; no puede listar toda la topología.
- Las credenciales de RabbitMQ quedan en el back. El front nunca habla directamente con RabbitMQ.

## API REST

Base path: `/api/admin`. Acceso vía BFF: **solo Admin**. Cualquier otro rol recibe 403.

| Método | Endpoint | Para qué sirve | Respuestas |
|---|---|---|---|
| `GET` | `/api/admin/overview` | Métricas del clúster: colas activas, mensajes pendientes, consumidores, mensajes en DLQ, estado de los nodos y la URL pública de la Management UI | 200; 503 si la Management API no responde |
| `GET` | `/api/admin/queues` | Todas las colas, con mensajes listos y sin confirmar, consumidores, su DLQ y su retención (TTL, largo máximo, overflow) | 200; 503 |
| `GET` | `/api/admin/queues/{name}` | Mensajes pendientes y consumidores de una cola, consultados por AMQP | 200; 404 |
| `GET` | `/api/admin/exchanges` | Exchanges de la topología, con su tipo (sin el exchange por defecto ni los `amq.*` internos) | 200; 503 |
| `GET` | `/api/admin/bindings` | Bindings exchange → cola, con su routing key | 200; 503 |
| `POST` | `/api/admin/queues` | Crear una cola. Opcional: **DLQ propia** y **política de retención** | **201**; 400; 409; 503 |
| `DELETE` | `/api/admin/queues/{name}` | Eliminar una cola | **204**; 404; 409 si es protegida |
| `POST` | `/api/admin/exchanges` | Crear un exchange `direct`, `topic` o `fanout` | **201**; 400; 409 |
| `DELETE` | `/api/admin/exchanges/{name}` | Eliminar un exchange | **204**; 404; 409 si es protegido |
| `POST` | `/api/admin/bindings` | Unir un exchange con una cola mediante una routing key | **201**; 400 si el exchange o la cola no existen; 409 |
| `DELETE` | `/api/admin/bindings` | Quitar un binding (mismo body que al crearlo) | **204**; 400; 409 |
| `GET` | `/actuator/health` | Indica si el micro está arriba | 200 |

### Ejemplos

Crear una cola con DLQ y retención:

```json
POST /api/admin/queues
{
  "name": "q.demo.reportes",
  "durable": true,
  "withDlq": true,
  "messageTtlMs": 60000,
  "maxLength": 1000,
  "overflow": "reject-publish-dlx"
}
```

| Campo | Obligatorio | Efecto en RabbitMQ |
|---|---|---|
| `name` | sí | Nombre de la cola |
| `durable` | no (`true`) | Sobrevive a un reinicio del broker |
| `withDlq` | no (`false`) | Crea `<name>.dlq` y la conecta vía `cmd.dead.dlx` (`x-dead-letter-exchange` / `x-dead-letter-routing-key`) |
| `messageTtlMs` | no | `x-message-ttl`: los mensajes expiran tras ese tiempo; si hay DLQ, pasan a ella |
| `maxLength` | no | `x-max-length`: largo máximo de la cola |
| `overflow` | no | `x-overflow` al llenarse: `drop-head` (descarta el más antiguo, el valor por defecto de RabbitMQ), `reject-publish` (rechaza lo nuevo) o `reject-publish-dlx` (lo rechazado va a la DLQ) |

Crear un exchange y unirlo a la cola:

```json
POST /api/admin/exchanges
{ "name": "ex.demo", "type": "topic" }
```

```json
POST /api/admin/bindings
{ "exchange": "ex.demo", "queue": "q.demo.reportes", "routingKey": "reporte.*" }
```

Métricas:

```json
GET /api/admin/overview
{
  "activeQueues": 8,
  "pendingMessages": 3,
  "consumers": 3,
  "dlqMessages": 2,
  "nodes": [ { "name": "rabbit@rabbit1", "running": true },
             { "name": "rabbit@rabbit2", "running": true } ],
  "managementUrl": "http://<ip-publica-ec2-mq>:15672"
}
```

## Validaciones y reglas

| Caso | Respuesta |
|---|---|
| Nombre vacío, de más de 250/255 caracteres o con caracteres fuera de `A-Z a-z 0-9 . _ -` | **400**, con el detalle por campo en `errors` |
| Tipo de exchange distinto de `direct`, `topic` o `fanout` | **400** |
| `messageTtlMs` o `maxLength` menores o iguales a 0; `overflow` desconocido | **400** |
| Routing key con espacios o caracteres inválidos (se admiten `*` y `#` para topic) | **400** |
| JSON mal formado o con tipos incorrectos | **400**, indicando el campo |
| Binding con un exchange o una cola que no existen | **400** |
| Crear algo que ya existe | **409** |
| Eliminar algo que no existe | **404** |
| **Recurso protegido:** crear o eliminar nombres que empiezan con `cmd.`, `q.cmd.` (la topología del sistema) o `amq.` (reservado por RabbitMQ), y tocar bindings de colas protegidas | **409** |
| RabbitMQ o su Management API no responden | **503** |

**Por qué hay recursos protegidos:** las colas y los exchanges de AndesStay (`q.cmd.notification`, `cmd.topic`…) los declaran reservations y notify. Si se borraran desde el administrador, el sistema dejaría de notificar. Sí se permite **unir una cola propia a un exchange del sistema**, por ejemplo para observar el tráfico de `cmd.topic`.

Cada operación de escritura queda registrada en el log con el prefijo `ADMIN`:

```bash
docker logs andesstay-rabbit-admin | grep ADMIN
```

## Métricas de monitoreo (para la presentación)

| Métrica | De dónde sale | Por qué importa |
|---|---|---|
| **Colas activas** | Cantidad de colas en el vhost `/` | Muestra que la topología esperada existe |
| **Mensajes pendientes** | `messages` = listos + sin confirmar | Si crece sostenidamente, los consumidores no dan abasto: hay que escalar o revisar errores |
| **Consumidores** | `consumers` por cola | Una cola de trabajo con 0 consumidores significa que su micro está caído |
| **Mensajes en DLQ** | `messages` de las colas `*.dlq` | Mensajes que fallaron y que nadie procesó; deberían ser 0 |
| **Nodos** | `/api/nodes` | Confirma que el clúster tiene sus 2 nodos corriendo |

## Estructura

```
ms_andesstay_rabbit_admin/
├── config/       RabbitAdminConfig (bean RabbitAdmin) · AdminProperties (URLs y prefijos protegidos)
├── client/       ManagementClient · ManagementApiModels (API HTTP de la Management UI)
├── dto/          CreateQueueRequest · CreateExchangeRequest · BindingRequest (validación) · AdminViews
├── service/      RabbitAdminService (escritura, AMQP) · MonitoringService (lectura y métricas)
├── domain/       ResourceNotFoundException · ResourceConflictException · ManagementUnavailableException
└── controller/   AdminController · GlobalExceptionHandler
```

## Configuración

`src/main/resources/application.yml`; en el contenedor se sobrescribe desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8086` | — |
| `spring.rabbitmq.host` / `port` | `localhost:5672` | `SPRING_RABBITMQ_HOST` (`MQ_HOST`) / `_PORT` |
| `spring.rabbitmq.username` / `password` | `andesstay` / `andesstay` | `SPRING_RABBITMQ_USERNAME` / `_PASSWORD` |
| `rabbit.management.url` | `http://localhost:15672` | `RABBIT_MANAGEMENT_URL` = `http://${MQ_HOST}:15672` (IP privada) |
| `rabbit.management.public-url` | `http://localhost:15672` | `RABBIT_MANAGEMENT_PUBLIC_URL` = `${MQ_PUBLIC_URL}` (IP pública, para el botón del front) |
| `rabbit.protected-prefixes` | `cmd.,q.cmd.,amq.` | — |

La Management API usa el mismo usuario y contraseña que AMQP.

**Red:** el Security Group de ec2-mq debe permitir el puerto **15672 desde ec2-apps**, para la API, y **desde tu IP**, para abrir la consola desde el botón. Ver [ec2-mq](../../ec2-mq/README.md).

## Tests

```bash
./mvnw test
```

- `RabbitAdminServiceTest`: creación con DLQ y retención, duplicados (409), recursos protegidos (409), eliminación de algo inexistente (404).
- `RequestValidationTest`: nombres vacíos o inválidos, tipos de exchange, retención y routing keys (lo que responde 400).

## Ejecutar localmente

Requiere RabbitMQ con la Management UI en `localhost` (5672 y 15672).

```bash
./mvnw spring-boot:run
```
