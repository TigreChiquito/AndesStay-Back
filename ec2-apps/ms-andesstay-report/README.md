# ms-andesstay-report

Microservicio de **reportería** (solo lectura). Mantiene un *read model* propio de reservas, construido a partir de los eventos que produce `ms-andesstay-reservations` en Kafka, y expone una API de KPIs para el negocio.

## Responsabilidades

- Consumir el tópico Kafka `reservations.events` y proyectar cada evento en su propia tabla (`ReservationProjection`).
- Exponer indicadores agregados: total de reservas, estadías activas, conteo por estado y por unidad.
- Manejar errores de deserialización/procesamiento enviando el mensaje fallido a una DLT propia (`reservations.events.DLT`) tras reintentar, sin bloquear el resto del consumo.

Este servicio **no expone API de escritura**: toda la información nace de eventos, nunca de un cliente REST.

## API REST

Base path: `/api/reports`

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/reports/summary` | Resumen general: total de reservas, estadías activas (`EN_ESTADIA`), conteo por estado y por unidad |
| `GET` | `/api/reports/by-status` | Cantidad de reservas por estado |
| `GET` | `/api/reports/by-unit` | Cantidad de reservas por unidad |

## Mensajería — consumidor de `reservations.events`

- `group-id` propio (distinto al de `ms-andesstay-audit`), para que ambos reciban todos los eventos de forma independiente (fan-out).
- El `value-deserializer` está envuelto en `ErrorHandlingDeserializer` para que un mensaje corrupto no rompa el loop de consumo.
- `DefaultErrorHandler` con `FixedBackOff` (1s, 2 reintentos) y `DeadLetterPublishingRecoverer`: si tras 3 intentos el mensaje sigue fallando, se publica en `reservations.events.DLT` manteniendo la misma partición.

Tipos de evento consumidos: `reservation.created`, `reservation.status_changed`.

## Configuración

Ver `src/main/resources/application.properties`. Este servicio solo define `spring.application.name`; usa los valores por defecto de Spring Boot para el resto (incluido el puerto HTTP, `8080`).

Para producción/entornos compartidos conviene fijar explícitamente al menos:

- `server.port`
- `spring.datasource.*` (persistencia del read model)
- `spring.kafka.bootstrap-servers` y `spring.kafka.consumer.group-id`

tal como están configurados en `ms-andesstay-audit`, que consume el mismo tópico.

## Ejecutar localmente

Requiere PostgreSQL, y Kafka corriendo en `localhost:9092` con el tópico `reservations.events` (lo produce `ms-andesstay-reservations`).

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8080/actuator/health` (o el puerto configurado).

## Tests

```bash
./mvnw test
```
