# ec2-mq — Máquina 2: RabbitMQ

Clúster **RabbitMQ de 2 nodos** con Management UI. Transporta los **comandos** ("haz esto") que `ms-andesstay-reservations` publica y que `ms-andesstay-notify` consume: avisos al huésped, avisos al personal (housekeeping) y vouchers.

Usa imágenes oficiales (`rabbitmq:3-management`), sin código propio.

## Contenido

| Archivo | Descripción |
|---|---|
| `compose.yml` | Los nodos `rabbit1` y `rabbit2` |
| `rabbitmq.conf` | Formación del clúster y política ante particiones (se monta en ambos nodos) |
| `.env.example` | Plantilla de variables (copiar a `.env`) |

## Nodos

| Servicio | Contenedor | Puertos publicados | Rol |
|---|---|---|---|
| `rabbit1` | `andesstay-rabbit1` | **5672** (AMQP), **15672** (Management UI) | Nodo al que se conectan los micros |
| `rabbit2` | `andesstay-rabbit2` | — | Segundo nodo del clúster |

Ambos tienen healthcheck (`rabbitmq-diagnostics ping`) y un volumen propio (`rabbit1_data`, `rabbit2_data`).

## Configuración del clúster (`rabbitmq.conf`)

```ini
cluster_formation.peer_discovery_backend = classic_config
cluster_formation.classic_config.nodes.1 = rabbit@rabbit1
cluster_formation.classic_config.nodes.2 = rabbit@rabbit2
cluster_partition_handling = autoheal
loopback_users = none
```

- **Descubrimiento declarativo:** los nodos se encuentran por la lista fija, usando el `hostname` de cada contenedor.
- **`autoheal`:** ante una partición de red, RabbitMQ elige una partición ganadora y reinicia la otra.
- **`loopback_users = none`:** permite que el usuario de la aplicación se conecte desde fuera del host. El usuario `guest` solo funciona desde localhost.
- Para formar el clúster, los dos nodos deben compartir la misma **Erlang cookie** (`RABBIT_COOKIE`).

> El archivo debe llamarse exactamente `rabbitmq.conf`, en minúsculas, porque así lo monta el compose. Si el nombre no coincide, Docker crea un directorio vacío en su lugar y los nodos no forman clúster.

## Variables de entorno (`.env`)

```bash
cp .env.example .env
```

| Variable | Descripción |
|---|---|
| `RABBIT_COOKIE` | **Obligatoria.** Secreto compartido por los nodos (Erlang cookie) |
| `RABBIT_USER` | Usuario de la aplicación (por defecto `andesstay`) |
| `RABBIT_PASSWORD` | **Obligatoria.** Su contraseña |

`RABBIT_USER` y `RABBIT_PASSWORD` deben coincidir con `MQ_USER` y `MQ_PASSWORD` del `.env` de [ec2-apps](../ec2-apps/README.md).

## Topología

La topología **no** se define aquí: la declaran los propios micros al arrancar (reservations y notify, de forma idempotente). Ver el detalle en [ms-andesstay-notify](../ec2-apps/ms-andesstay-notify/README.md#topología-rabbitmq).

- Exchanges: `cmd.direct`, `cmd.topic` y `cmd.dead.dlx`.
- Colas: `q.cmd.notification`, `q.cmd.housekeeping`, `q.cmd.voucher`, cada una con su `.dlq`.

> **Migración desde `q.cmd.email`:** la cola de avisos al huésped se llamaba `q.cmd.email`. Tras desplegar la versión nueva, borra `q.cmd.email` y `q.cmd.email.dlq` desde la Management UI (*Queues → cola → Delete*). Ya nadie publica ni consume en ellas.

## Despliegue

```bash
cp .env.example .env   # y completar
docker compose up -d
```

Verificar el clúster:

```bash
docker exec andesstay-rabbit1 rabbitmq-diagnostics cluster_status
```

Management UI: `http://<IP_EC2_MQ>:15672`, con `RABBIT_USER` / `RABBIT_PASSWORD`. En la pestaña *Queues* se ven los mensajes de cada cola y de las DLQ.

## Red / Security Group

| Puerto | Origen permitido | Uso |
|---|---|---|
| 5672 | ec2-apps | AMQP (micros) |
| 15672 | Tu IP (solo administración) | Management UI |

## Limitaciones conocidas

- Los micros se conectan **solo a `rabbit1`**. Si ese nodo cae, no hay *failover* automático hacia `rabbit2`.
- Las colas son *classic* (no *quorum*): sus mensajes viven en un solo nodo. Para alta disponibilidad real habría que declararlas como `x-queue-type: quorum` y configurar ambos nodos en `spring.rabbitmq.addresses`.
