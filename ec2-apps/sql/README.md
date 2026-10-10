# Datos de prueba (seed)

Scripts SQL para poblar las 4 bases de datos de AndesStay con datos mock,
pensados para correr una vez desplegado en AWS y tener algo con qué probar
sin depender de que Kafka/RabbitMQ estén perfectamente orquestados.

Insertan directamente en cada base (no pasan por las APIs), así que no
requieren que los microservicios estén arriba — solo Postgres.

## Orden de ejecución (hay dependencias entre bases)

1. `seed-catalog.sql` → **catalog_db** (20 unidades: hostales, cabañas, lodges)
2. `seed-reservations.sql` → **reservations_db** (15 reservas que referencian
   los `unit_id` del paso 1, cubriendo todo el ciclo de vida: CREADA,
   CONFIRMADA, CHECKIN_PENDIENTE, EN_ESTADIA, CHECKOUT, CANCELADA)
3. `seed-catalog-update-availability.sql` → **catalog_db** (descuenta 1 cupo
   en las unidades cuya reserva del paso 2 quedó "activa" — CONFIRMADA,
   CHECKIN_PENDIENTE o EN_ESTADIA — igual que lo haría reservations vía REST)
4. `seed-audit.sql` → **audit_db** (un evento `reservation.created` por cada
   reserva del paso 2, más un `reservation.status_changed` por cada
   transición de estado que tuvo — reconstruye la traza que en producción
   llega por Kafka)
5. `seed-report.sql` → **report_db** (proyección con el estado ACTUAL de cada
   reserva del paso 2, para tener KPIs de prueba)

Cada archivo es **idempotente-consciente pero no re-ejecutable**: están
pensados para correr UNA sola vez sobre una base recién creada/vacía. Si se
corren dos veces se duplican filas (catalog, audit) o falla por choque de
PK (reservations, report).

## Cómo ejecutarlos en la EC2

Asumiendo un contenedor Postgres llamado `andesstay-postgres` con las 4
bases ya creadas (`catalog_db`, `reservations_db`, `audit_db`, `report_db`):

```bash
docker exec -i andesstay-postgres psql -U andesstay -d catalog_db      < seed-catalog.sql
docker exec -i andesstay-postgres psql -U andesstay -d reservations_db < seed-reservations.sql
docker exec -i andesstay-postgres psql -U andesstay -d catalog_db      < seed-catalog-update-availability.sql
docker exec -i andesstay-postgres psql -U andesstay -d audit_db        < seed-audit.sql
docker exec -i andesstay-postgres psql -U andesstay -d report_db       < seed-report.sql
```

Las tablas las crea Hibernate solo (`ddl-auto: update`), así que cada
microservicio debe haber arrancado **al menos una vez** contra su base antes
de correr el seed correspondiente (si no, la tabla no existe todavía).

## `seed-reservations.sh`

Alternativa que puebla `reservations_db` (y, en cascada vía Kafka,
`audit_db`/`report_db`) llamando a las APIs reales en vez de INSERTs
directos. Útil para probar el flujo end-to-end (incluyendo Kafka), pero
requiere que `catalog`, `reservations`, `audit`, `report` y Kafka estén
todos arriba y bien conectados — más frágil que los scripts SQL de arriba
para un primer smoke test post-deploy.

Crea hasta 10 reservas (una por unidad) y las confirma, así que descuenta
cupos reales en catalog y genera avisos y vouchers en notify.

Las URLs se configuran con las variables `RES` y `CAT` (por defecto
`localhost:8081` y `localhost:8082`, para desarrollo local). En ec2-apps los
micros **no publican puertos en el host**, así que hay que correrlo desde un
contenedor conectado a la red del compose (`andesstay-apps_default`):

```bash
docker run --rm --network andesstay-apps_default -v "$PWD":/seed \
  -e RES=http://reservations:8081 -e CAT=http://catalog:8082 \
  alpine:3 sh -c "apk add -q bash curl && bash /seed/seed-reservations.sh"
```

Llama a los micros directamente, sin pasar por el BFF, así que no necesita
token de Azure AD.
