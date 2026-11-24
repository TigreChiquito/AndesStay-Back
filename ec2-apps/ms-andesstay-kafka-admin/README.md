# ms-andesstay-kafka-admin

Administrador de la plataforma Kafka (puerto **8087**). Usa el `AdminClient` de Kafka para gestionar **tópicos**, su **configuración** y los **consumer groups** (lag y replay). El BFF lo expone en `/api/kafka/**`, **solo para el rol `Admin`**.

Es el equivalente a `kafka-topics.sh`, `kafka-configs.sh` y `kafka-consumer-groups.sh`, pero como API REST y con reglas que protegen los tópicos del sistema.

## Reglas de protección

| Tópico | Ver | Ajustar configuración | Aumentar particiones | Eliminar |
|---|---|---|---|---|
| Del sistema (`reservations.*`, `units.*`) | ✅ | ✅ | ❌ (cambiaría la partición de cada clave y se pierde el orden) | ❌ |
| Internos de Kafka (`_*`, ej. `__consumer_offsets`) | ✅ | ❌ | ❌ | ❌ |
| Creados desde el administrador | ✅ | ✅ | ✅ | ✅ |

- Solo se pueden tocar estas configuraciones: `cleanup.policy`, `retention.ms`, `retention.bytes`, `segment.ms`, `min.insync.replicas`, `max.message.bytes` y `compression.type`.
- Los prefijos protegidos se configuran en `kafka-admin.protected-prefixes`.
- Los micros declaran la configuración de sus tópicos en código, con `modify-topic-configs: true`. Si se cambia la retención de `reservations.events` desde aquí, al reiniciar reservations vuelve al valor del código.

## API REST

Base: `/api/kafka`. Los errores usan `ProblemDetail`: **400** si la petición es inválida o Kafka la rechaza, **404** si no existe, **409** si está protegido, ya existe o el grupo está activo, y **503** si Kafka no responde.

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/cluster` | ID del clúster, brokers (marca el controlador), nº de tópicos y de grupos |
| GET | `/topics` | Tópicos con particiones, réplicas y si están protegidos |
| GET | `/topics/{name}` | Particiones (líder, réplicas, ISR) y configuración editable (`isDefault` = viene del broker) |
| POST | `/topics` | Crea un tópico. 201 |
| PUT | `/topics/{name}/configs` | Cambia la configuración. Un valor vacío quita el override y vuelve al default |
| POST | `/topics/{name}/partitions` | Aumenta las particiones (Kafka no permite bajarlas) |
| DELETE | `/topics/{name}` | Elimina el tópico. 204 |
| GET | `/groups` | Consumer groups con estado, nº de miembros y lag total |
| GET | `/groups/{groupId}` | Miembros con sus particiones asignadas, y offset confirmado, fin del log y lag por partición |
| POST | `/groups/{groupId}/reset-offsets` | Mueve los offsets del grupo en un tópico (replay). El grupo debe estar **sin consumidores** |

Ejemplos de cuerpos:

```json
// POST /topics
{ "name": "pruebas.carga", "partitions": 3, "replicationFactor": 3,
  "configs": { "retention.ms": "60000", "cleanup.policy": "delete" } }

// PUT /topics/pruebas.carga/configs
{ "configs": { "retention.ms": "86400000", "retention.bytes": "" } }

// POST /topics/pruebas.carga/partitions
{ "totalPartitions": 6 }

// POST /groups/ms-andesstay-report/reset-offsets   (to: EARLIEST | LATEST | DATETIME)
{ "topic": "reservations.events", "to": "DATETIME", "datetime": "2026-11-21T12:00:00Z" }
```

### Replay de un grupo del sistema

Kafka solo mueve los offsets de un grupo sin consumidores conectados. Por ejemplo, para que report reprocese todo `reservations.events`:

```bash
docker compose stop report
# POST /api/kafka/groups/ms-andesstay-report/reset-offsets  {"topic":"reservations.events","to":"EARLIEST"}
docker compose start report
```

## Configuración

| Propiedad | Variable | Por defecto |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `kafka-admin.protected-prefixes` | — | `reservations.,units.,_` |

Los timeouts del cliente son cortos (`request.timeout.ms` 5 s y `default.api.timeout.ms` 10 s). Así, si Kafka no responde, la API devuelve 503 antes de que el API Gateway corte a los 30 s.

## Pruebas

```bash
./mvnw test
```

`KafkaAdminServicesTest` cubre las reglas de protección, la lista blanca de configuraciones, la traducción de errores de Kafka y el cálculo del lag.
