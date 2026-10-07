# postgres-init

Scripts de inicialización del contenedor `andesstay-postgres`. El [`compose.yml`](../compose.yml) monta esta carpeta (solo lectura) en `/docker-entrypoint-initdb.d`. La imagen oficial de Postgres ejecuta en orden alfabético los `.sql` que encuentra ahí.

## Qué crea

AndesStay usa **un solo motor Postgres con una base por microservicio** (*database-per-service*):

| Base | Micro | La crea |
|---|---|---|
| `reservations_db` | reservations | La variable `POSTGRES_DB` del compose |
| `catalog_db` | catalog | [`01 create databases.sql`](01%20create%20databases.sql) |
| `audit_db` | audit | `01 create databases.sql` |
| `report_db` | report | `01 create databases.sql` |

notify y el BFF no tienen base. Todas las bases quedan como propiedad del usuario `DB_USER` (por defecto `andesstay`).

Las **tablas** no se crean aquí: las genera Hibernate (`ddl-auto: update`) cuando cada micro arranca por primera vez.

## Cuándo se ejecuta

**Solo la primera vez**, cuando el volumen `andesstay_pgdata` está vacío. Si el volumen ya existe, Postgres ignora esta carpeta, aunque se modifique el script.

Si hace falta agregar una base nueva con el volumen ya creado, hay dos opciones:

- Crearla a mano (no borra nada):

  ```bash
  docker exec -it andesstay-postgres psql -U andesstay -d reservations_db -c "CREATE DATABASE nueva_db;"
  ```

- Recrear el volumen. ⚠️ **Esto borra todos los datos:**

  ```bash
  docker compose down -v && docker compose up -d
  ```

## Notas

- Si cambias `DB_PASSWORD` después de crear el volumen, Postgres conserva la contraseña original y los micros fallarán al conectarse. Hay que cambiarla con `ALTER USER`, o recrear el volumen.
- El nombre del archivo tiene espacios (`01 create databases.sql`). Funciona, pero conviene renombrarlo a `01-create-databases.sql` para evitar problemas al citarlo en scripts.
