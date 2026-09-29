-- ============================================================
-- Ajusta cupos disponibles (base: catalog_db, tabla: units) para que
-- coincidan con las reservas de seed-reservations.sql.
--
-- Por que un script aparte: reservations_db y catalog_db son bases
-- distintas (cada microservicio tiene la suya), asi que no se puede
-- actualizar catalog en el mismo script que inserta las reservas.
-- En la app real, esto lo hace reservations llamando a catalog via REST
-- cada vez que una reserva pasa a CONFIRMADA (resta 1 cupo) o sale de
-- CONFIRMADA/CHECKIN_PENDIENTE hacia CHECKOUT/CANCELADA (devuelve 1 cupo).
--
-- Unidades con una reserva "activa" (CONFIRMADA, CHECKIN_PENDIENTE o
-- EN_ESTADIA en seed-reservations.sql) deben quedar con 1 cupo menos:
--   2  (Diego Pino, CONFIRMADA)          9  (Elena Vera, CONFIRMADA)
--   15 (Fabian Rojas, CONFIRMADA)        16 (Gina Lara, CONFIRMADA)
--   4  (Hugo Mena, CHECKIN_PENDIENTE)    10 (Ivo Salas, CHECKIN_PENDIENTE)
--   5  (Julia Toro, EN_ESTADIA)          11 (Karen Leiva, EN_ESTADIA)
--
-- Las unidades 1,3,7,8,17 (CREADA/CANCELADA, nunca confirmaron o ya se
-- devolvio el cupo) y 6,12 (CHECKOUT, cupo ya devuelto) quedan con el
-- cupo lleno, tal como las dejo seed-catalog.sql.
--
-- Requiere haber corrido antes seed-catalog.sql. Ejecutar UNA sola vez.
--
-- Como ejecutarlo en la EC2 (dentro de ec2-apps):
--   docker exec -i andesstay-postgres psql -U andesstay -d catalog_db < seed-catalog-update-availability.sql
-- ============================================================

UPDATE units
   SET available_slots = available_slots - 1,
       updated_at = now()
 WHERE id IN (2, 4, 5, 9, 10, 11, 15, 16)
   AND available_slots > 0;

-- Verificar:
--   SELECT id, name, total_slots, available_slots FROM units ORDER BY id;
