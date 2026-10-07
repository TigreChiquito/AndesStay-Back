# ec2-kafka — Máquina 3: Kafka

Clúster **Kafka de 3 brokers** con **Zookeeper de 3 nodos** y Kafka UI. Transporta los **eventos** ("esto pasó") de las reservas: `ms-andesstay-reservations` los produce en `reservations.events`, y `ms-andesstay-report` y `ms-andesstay-audit` los consumen.

Usa imágenes oficiales de Confluent (`cp-zookeeper:7.6.1` y `cp-kafka:7.6.1`) y `provectuslabs/kafka-ui`, sin código propio.

## Contenido

| Archivo | Descripción |
|---|---|
| `compose.yml` | zk1-3, kafka1-3 y kafka-ui |
| `.env.example` | Plantilla de variables (copiar a `.env`) |

## Servicios

| Servicio | Contenedor | Puerto publicado | Rol |
|---|---|---|---|
| `zk1`, `zk2`, `zk3` | `andesstay-zk1..3` | — | Ensemble de Zookeeper (metadatos del clúster) |
| `kafka1` | `andesstay-kafka1` | **9092** | Broker 1 |
| `kafka2` | `andesstay-kafka2` | **9093** | Broker 2 |
| `kafka3` | `andesstay-kafka3` | **9094** | Broker 3 |
| `kafka-ui` | `andesstay-kafka-ui` | **8090** | UI web para ver tópicos, mensajes y grupos de consumo |

## Listeners

Cada broker tiene dos listeners:

| Listener | Puerto en el contenedor | Anunciado como | Lo usan |
|---|---|---|---|
| `INTERNAL` | 29092 | `kafkaN:29092` | Comunicación entre brokers y Kafka UI |
| `EXTERNAL` | 9092 | `${KAFKA_EXTERNAL_HOST}:9092 / 9093 / 9094` | Los micros de ec2-apps |

Al conectarse, el cliente recibe la dirección **anunciada** de cada broker y desde ahí se conecta directamente a ella. Por eso `KAFKA_EXTERNAL_HOST` tiene que ser una IP alcanzable desde ec2-apps. Si no lo es, el productor de reservations se queda esperando metadatos.

## Replicación

| Parámetro | Valor | Efecto |
|---|---|---|
| `KAFKA_DEFAULT_REPLICATION_FACTOR` | 3 | Los tópicos auto-creados tienen 3 réplicas |
| `KAFKA_MIN_INSYNC_REPLICAS` | 2 | Con `acks=all`, una escritura necesita al menos 2 réplicas sincronizadas |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | 3 | Los offsets de los consumidores también se replican |

El clúster tolera la caída de **1 broker** sin perder escrituras.

> Un tópico creado con 1 réplica **no puede recibir escrituras** con `acks=all`, porque nunca alcanza las 2 réplicas sincronizadas. Por eso ec2-apps fija `ANDESSTAY_KAFKA_REPLICAS=3` en los micros que declaran tópicos.

## Tópicos

| Tópico | Productor | Consumidores |
|---|---|---|
| `reservations.events` | reservations (key = `reservationId`) | report (`ms-andesstay-report`), audit (`ms-andesstay-audit`) |
| `reservations.events.DLT` | report (mensajes fallidos) | — |
| `reservations.events.audit.DLT` | audit (mensajes fallidos) | — |

## Variables de entorno (`.env`)

```bash
cp .env.example .env
```

| Variable | Descripción |
|---|---|
| `KAFKA_EXTERNAL_HOST` | **IP privada de esta máquina** (por ejemplo `10.0.7.221`). Es la dirección que los brokers anuncian a los clientes |

En el `.env` de [ec2-apps](../ec2-apps/README.md):

```
KAFKA_BOOTSTRAP=<KAFKA_EXTERNAL_HOST>:9092,<KAFKA_EXTERNAL_HOST>:9093,<KAFKA_EXTERNAL_HOST>:9094
```

## Despliegue

```bash
cp .env.example .env   # con la IP privada real
docker compose up -d
```

Si cambias `KAFKA_EXTERNAL_HOST`, recrea los brokers con `docker compose up -d` para que anuncien la nueva dirección.

### Comandos útiles

```bash
# Listar tópicos
docker exec andesstay-kafka1 kafka-topics --bootstrap-server kafka1:29092 --list

# Ver particiones, réplicas e ISR
docker exec andesstay-kafka1 kafka-topics --bootstrap-server kafka1:29092 --describe --topic reservations.events

# Subir particiones (no se pueden bajar)
docker exec andesstay-kafka1 kafka-topics --bootstrap-server kafka1:29092 --alter --topic reservations.events --partitions 3

# Lag de un grupo de consumo
docker exec andesstay-kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 --describe --group ms-andesstay-audit
```

Kafka UI: `http://<IP_EC2_KAFKA>:8090`.

## Red / Security Group

| Puerto | Origen permitido | Uso |
|---|---|---|
| 9092-9094 | ec2-apps | Brokers (micros) |
| 8090 | Tu IP (solo administración) | Kafka UI |

## Notas

- Son 7 contenedores Java y consumen bastante RAM: se recomiendan al menos 4 GB.
- Si `reservations.events` fue auto-creado antes de que reservations lo declarara, puede quedar con 1 partición en vez de 3. Funciona igual, pero sin paralelismo. Se corrige con el `--alter` de arriba.
