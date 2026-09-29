-- ============================================================
-- Datos de prueba para reservas (base: reservations_db, tabla: reservations)
--
-- Requiere haber corrido antes seed-catalog.sql contra catalog_db (asume
-- unidades con id 1..20 en el mismo orden de inserción; la 20 -Hostal
-- Talagante- queda inactiva y por eso no se usa aquí).
--
-- Los id se fijan explícitamente (1..15) para que seed-audit.sql y
-- seed-report.sql puedan referenciar el mismo reservationId. Por eso al
-- final se reajusta la secuencia de la PK (si no, el próximo INSERT de la
-- app chocaría con estos id).
--
-- Cómo ejecutarlo en la EC2 (dentro de ec2-apps):
--   docker exec -i andesstay-postgres psql -U andesstay -d reservations_db < seed-reservations.sql
--
-- Ejecutar UNA sola vez (correrlo dos veces duplica filas con otros id).
-- ============================================================

INSERT INTO reservations
  (id, guest_id, guest_name, unit_id, check_in_date, check_out_date, status, created_at, updated_at, version)
VALUES
  -- CREADA: recien creadas, aun no confirman cupo en catalog
  (1,  'u-201', 'Ana Soto',      1,  '2026-10-15', '2026-10-18', 'CREADA',            '2026-09-27 09:00:00-04', '2026-09-27 09:00:00-04', 0),
  (2,  'u-202', 'Beto Diaz',     3,  '2026-10-20', '2026-10-22', 'CREADA',            '2026-09-28 11:30:00-04', '2026-09-28 11:30:00-04', 0),
  (3,  'u-203', 'Carla Ruiz',    7,  '2026-11-01', '2026-11-03', 'CREADA',            '2026-09-29 08:15:00-04', '2026-09-29 08:15:00-04', 0),

  -- CONFIRMADA: ya descontaron cupo en catalog (ver seed-catalog-update-availability.sql)
  (4,  'u-204', 'Diego Pino',    2,  '2026-10-05', '2026-10-08', 'CONFIRMADA',        '2026-09-20 10:00:00-04', '2026-09-21 09:00:00-04', 1),
  (5,  'u-205', 'Elena Vera',    9,  '2026-10-10', '2026-10-12', 'CONFIRMADA',        '2026-09-21 14:20:00-04', '2026-09-22 10:00:00-04', 1),
  (6,  'u-206', 'Fabian Rojas',  15, '2026-10-18', '2026-10-20', 'CONFIRMADA',        '2026-09-22 16:45:00-04', '2026-09-23 08:30:00-04', 1),
  (7,  'u-207', 'Gina Lara',     16, '2026-10-25', '2026-10-28', 'CONFIRMADA',        '2026-09-23 12:10:00-04', '2026-09-24 09:40:00-04', 1),

  -- CHECKIN_PENDIENTE: confirmadas y a un paso del check-in
  (8,  'u-208', 'Hugo Mena',     4,  '2026-09-30', '2026-10-02', 'CHECKIN_PENDIENTE', '2026-09-18 09:30:00-04', '2026-09-28 09:00:00-04', 2),
  (9,  'u-209', 'Ivo Salas',     10, '2026-10-01', '2026-10-03', 'CHECKIN_PENDIENTE', '2026-09-19 15:00:00-04', '2026-09-28 15:00:00-04', 2),

  -- EN_ESTADIA: huesped actualmente alojado (check-in ya paso, check-out no)
  (10, 'u-210', 'Julia Toro',    5,  '2026-09-25', '2026-10-01', 'EN_ESTADIA',        '2026-09-10 09:00:00-04', '2026-09-25 12:00:00-04', 3),
  (11, 'u-211', 'Karen Leiva',   11, '2026-09-27', '2026-10-03', 'EN_ESTADIA',        '2026-09-12 10:15:00-04', '2026-09-27 12:30:00-04', 3),

  -- CHECKOUT: estadia terminada, cupo ya devuelto a catalog
  (12, 'u-212', 'Luis Campos',   6,  '2026-09-10', '2026-09-15', 'CHECKOUT',          '2026-08-28 09:00:00-04', '2026-09-15 11:00:00-04', 4),
  (13, 'u-213', 'Marta Nunez',   12, '2026-09-15', '2026-09-20', 'CHECKOUT',          '2026-09-01 09:00:00-04', '2026-09-20 11:00:00-04', 4),

  -- CANCELADA: una nunca llego a confirmar, la otra se cancelo ya confirmada
  (14, 'u-214', 'Nico Fuentes',  8,  '2026-11-10', '2026-11-12', 'CANCELADA',         '2026-09-24 09:00:00-04', '2026-09-25 09:00:00-04', 1),
  (15, 'u-215', 'Olga Reyes',    17, '2026-10-08', '2026-10-10', 'CANCELADA',         '2026-09-15 09:00:00-04', '2026-09-19 09:00:00-04', 2);

-- Evita choque de PK con el proximo id que genere la app (columna IDENTITY).
SELECT setval(pg_get_serial_sequence('reservations', 'id'), (SELECT MAX(id) FROM reservations));

-- Verificar:
--   SELECT id, guest_name, unit_id, status, check_in_date, check_out_date FROM reservations ORDER BY id;
