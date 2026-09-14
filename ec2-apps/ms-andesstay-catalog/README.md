# ms-andesstay-catalog

Microservicio dueño del **catálogo de unidades de hospedaje** de AndesStay (hostales, cabañas y lodges) y de su disponibilidad (cupos).

## Responsabilidades

- CRUD de unidades de hospedaje.
- Búsqueda de unidades por tipo y disponibilidad.
- Control de cupos: descuenta un cupo cuando `ms-andesstay-reservations` confirma una reserva y lo devuelve cuando la cancela.

## Modelo

Una `Unit` tiene: `name`, `type` (`HOSTAL`, `CABANA`, `LODGE`), `location`, `totalSlots` (cupo total, no cambia con las reservas), `availableSlots` (cupo disponible, sube y baja), `pricePerNight` y `active`.

El conteo de disponibilidad es un modelo de dominio rico: `availableSlots` solo se modifica a través de `reserveOne()` / `releaseOne()` en la propia entidad, nunca escribiendo el campo directamente.

`UnitType` acepta variantes con o sin tilde/ñ al deserializar desde JSON (p. ej. `"CABAÑA"` se normaliza a `CABANA`).

## API REST

Base path: `/api/units`

| Método | Endpoint | Descripción |
|---|---|---|
| `POST` | `/api/units` | Crea una unidad (nace activa, con `availableSlots = totalSlots`) |
| `GET` | `/api/units/{id}` | Obtiene una unidad por id |
| `GET` | `/api/units?type=&available=` | Búsqueda por tipo y/o solo disponibles |
| `PUT` | `/api/units/{id}` | Actualiza datos, tarifa, estado y cupo total |
| `DELETE` | `/api/units/{id}` | Elimina la unidad |
| `POST` | `/api/units/{id}/reserve` | Descuenta un cupo (lo usa reservations al confirmar) |
| `POST` | `/api/units/{id}/release` | Devuelve un cupo (lo usa reservations al cancelar) |

Ejemplo de creación:

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

Errores de dominio manejados vía `GlobalExceptionHandler`: unidad no encontrada (`UnitNotFoundException`) y sin cupo disponible (`NoAvailabilityException`).

## Configuración

Ver `src/main/resources/Application.yml`.

| Propiedad | Valor por defecto | Descripción |
|---|---|---|
| `server.port` | `8082` | Puerto HTTP |
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/catalog_db` | Base de datos propia |
| `spring.datasource.password` | `andesstay` | Sobrescribible con `DB_PASSWORD` |

`spring.jpa.hibernate.ddl-auto=update` está pensado solo para desarrollo.

## Ejecutar localmente

Requiere PostgreSQL (`catalog_db`) corriendo en `localhost`.

```bash
./mvnw spring-boot:run
```

Health check: `GET http://localhost:8082/actuator/health`.

## Tests

```bash
./mvnw test
```
