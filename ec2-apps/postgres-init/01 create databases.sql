-- Se ejecuta UNA sola vez, al crear el volumen por primera vez.
-- reservations_db la crea POSTGRES_DB; aca creamos el resto.
-- notify y bff no tienen base.
 
CREATE DATABASE catalog_db;
CREATE DATABASE audit_db;
CREATE DATABASE report_db;
 