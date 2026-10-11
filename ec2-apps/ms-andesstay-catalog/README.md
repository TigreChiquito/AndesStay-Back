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

| Método | Endpoint | Para qué sirve | Acceso (vía BFF) | Respuestas |
|---|---|---|---|---|
| `POST` | `/api/units` | Dar de alta una unidad. Nace activa y con todos sus cupos libres (`availableSlots = totalSlots`) | Admin | **201** + `Location`; 400 si el payload es inválido |
| `GET` | `/api/units` | Listar el catálogo. Filtros opcionales: `type` (`HOSTAL`, `CABANA`, `LODGE`) y `available=true` (solo activas con cupo) | Autenticado | 200 |
| `GET` | `/api/units/{id}` | Ver el detalle de una unidad | Autenticado | 200; 404 si no existe |
| `PUT` | `/api/units/{id}` | Editar una unidad: nombre, tipo, ubicación, tarifa, cupo total y si está activa. Reajusta los cupos disponibles | Admin | 200; 400; 404 |
| `DELETE` | `/api/units/{id}` | Eliminar una unidad (borrado físico) | Admin | **204**; 404 |
| `POST` | `/api/units/{id}/reserve` | Descontar un cupo. Lo llama reservations al confirmar una reserva | Interno (reservations) | 200; 409 sin cupo o por conflicto de concurrencia |
| `POST` | `/api/units/{id}/release` | Devolver un cupo. Lo llama reservations al cancelar una reserva confirmada o al hacer checkout | Interno (reservations) | 200; 404 |
| `GET` | `/actuator/health` | Indica si el micro está arriba; lo usan los healthchecks | Interno (red de Docker) | 200 `{"status":"UP"}` |

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
