# ms-andesstay-catalog

Microservicio dueño del **catálogo de unidades de hospedaje** (hostales, cabañas y lodges) y de su **disponibilidad** (cupos). reservations lo llama por REST para descontar o devolver cupos. No usa mensajería.

## Responsabilidades

- CRUD de unidades de hospedaje.
- Búsqueda por tipo y disponibilidad.
- Control de cupos sin overbooking: descuenta un cupo cuando reservations confirma una reserva y lo devuelve cuando se cancela o termina.

## Modelo

`Unit` (tabla `units`):

| Campo | Descripción |
|---|---|
| `name` | Nombre de la unidad (obligatorio) |
| `type` | `HOSTAL`, `CABANA` o `LODGE` |
| `location` | Ubicación (texto libre) |
| `totalSlots` | Cupo total; solo cambia al editar la unidad |
| `availableSlots` | Cupo disponible: sube y baja con las reservas |
| `pricePerNight` | Tarifa (`BigDecimal`, nunca `double`) |
| `active` | Si la unidad está habilitada |
| `version` | Bloqueo optimista (`@Version`) |

- **Modelo de dominio rico:** `availableSlots` solo cambia por medio de `reserveOne()`, `releaseOne()` y `adjustTotalSlots()`, nunca escribiendo el campo directamente.
  - `reserveOne()` lanza `NoAvailabilityException` si no quedan cupos (→ 409).
  - `releaseOne()` nunca supera `totalSlots`.
  - `adjustTotalSlots(n)` suma la diferencia a los disponibles, con un mínimo de 0.
- **Bloqueo optimista:** si dos confirmaciones compiten por el último cupo, una gana y la otra recibe **409** en vez de dejar `availableSlots` en negativo.
- `UnitType` acepta tildes y ñ al deserializar: `"Cabaña"` se interpreta como `CABANA`. Cualquier otro valor (por ejemplo, `CAMPING`) da **400**.

## API REST

Base path: `/api/units`

| Método | Endpoint | Respuesta |
|---|---|---|
| `POST` | `/api/units` | **201**. La unidad nace activa y con `availableSlots = totalSlots` |
| `GET` | `/api/units/{id}` | 200, o 404 |
| `GET` | `/api/units?type=&available=` | 200. `type` es opcional; `available=true` devuelve solo las activas con cupo > 0 |
| `PUT` | `/api/units/{id}` | 200. Actualiza todos los campos editables; requiere `active` |
| `DELETE` | `/api/units/{id}` | **204**. Borrado físico |
| `POST` | `/api/units/{id}/reserve` | 200, o 409 sin cupo. Lo usa reservations al confirmar |
| `POST` | `/api/units/{id}/release` | 200. Lo usa reservations al cancelar o hacer checkout |

```json
POST /api/units
{
  "name": "Cabaña El Roble",
  "type": "CABANA",
  "location": "Puerto Varas",
  "totalSlots": 4,
  "pricePerNight": 45000
}
```

Respuesta (`UnitResponse`): `id, name, type, location, totalSlots, availableSlots, pricePerNight, active, createdAt, updatedAt`.

> Sin `available=true`, el listado incluye también las unidades inactivas: el front filtra por `active`.

### Errores (ProblemDetail, RFC 7807)

| Código | Cuándo |
|---|---|
| 400 | Payload inválido (campos obligatorios, `totalSlots` o `pricePerNight` ≤ 0) o `type` desconocido |
| 404 | La unidad no existe |
| 409 | Sin cupo (`NoAvailabilityException`) o conflicto de bloqueo optimista |

## Acceso a través del BFF

Desde el front, las lecturas (`GET`) están permitidas a cualquier usuario autenticado, y las escrituras requieren el rol `Admin`. reservations, en cambio, llama a `/reserve` y `/release` directamente por la red interna (`http://catalog:8082`), sin pasar por el BFF.

## Estructura

```
ms_andesstay_catalog/
├── domain/       Unit · UnitType · NoAvailabilityException · UnitNotFoundException
├── repository/   UnitRepository
├── dto/          CreateUnitRequest · UpdateUnitRequest · UnitResponse
├── service/      CatalogService
└── controller/   UnitController · GlobalExceptionHandler
```

## Configuración

`src/main/resources/application.yml`; en el contenedor se sobrescribe desde [`../compose.yml`](../compose.yml).

| Propiedad | Default | Variable en el compose |
|---|---|---|
| `server.port` | `8082` | — |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/catalog_db` | `SPRING_DATASOURCE_URL` |
| `spring.datasource.password` | `andesstay` | `SPRING_DATASOURCE_PASSWORD` |

`ddl-auto: update` crea y actualiza la tabla automáticamente.

## Ejecutar localmente

Requiere PostgreSQL (`catalog_db`) en `localhost`.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8082/actuator/health`.
