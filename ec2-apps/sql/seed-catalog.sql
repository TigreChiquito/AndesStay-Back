-- ============================================================
-- Datos de prueba para el catálogo (base: catalog_db, tabla: units)
-- Ejecutar UNA sola vez (los id son autogenerados; correrlo dos veces duplica).
--
-- Cómo ejecutarlo en la EC2 (dentro de ec2-apps):
--   docker exec -i andesstay-postgres psql -U andesstay -d catalog_db < seed-catalog.sql
--
-- O pegando el contenido en una sesión psql:
--   docker exec -it andesstay-postgres psql -U andesstay -d catalog_db
-- ============================================================

INSERT INTO units
  (name, type, location, total_slots, available_slots, price_per_night, active, created_at, updated_at, version)
VALUES
  -- Hostales (urbanos)
  ('Hostal Bellavista Centro', 'HOSTAL', 'Santiago Centro',      16, 16,  18000.00, true, now(), now(), 0),
  ('Hostal Lastarria',         'HOSTAL', 'Santiago Centro',      12, 12,  22000.00, true, now(), now(), 0),
  ('Hostal Barrio Brasil',     'HOSTAL', 'Santiago Centro',      20, 20,  15000.00, true, now(), now(), 0),
  ('Hostal Providencia',       'HOSTAL', 'Providencia',          14, 14,  25000.00, true, now(), now(), 0),
  ('Hostal Nunoa Plaza',       'HOSTAL', 'Nunoa',                10, 10,  20000.00, true, now(), now(), 0),
  ('Hostal Maipu',             'HOSTAL', 'Maipu',                12, 12,  16000.00, true, now(), now(), 0),

  -- Cabañas (Cajón del Maipo y alrededores)
  ('Cabana El Manzano',        'CABANA', 'San Jose de Maipo',     4,  4,  45000.00, true, now(), now(), 0),
  ('Cabana Rio Maipo',         'CABANA', 'San Jose de Maipo',     6,  6,  55000.00, true, now(), now(), 0),
  ('Cabana Los Quillayes',     'CABANA', 'Cajon del Maipo',       4,  4,  48000.00, true, now(), now(), 0),
  ('Cabana Pirque Vista',      'CABANA', 'Pirque',                5,  5,  60000.00, true, now(), now(), 0),
  ('Cabana El Toyo',           'CABANA', 'Cajon del Maipo',       3,  3,  42000.00, true, now(), now(), 0),
  ('Cabana Banos Morales',     'CABANA', 'San Jose de Maipo',     4,  4,  50000.00, true, now(), now(), 0),
  ('Cabana Melipilla Campo',   'CABANA', 'Melipilla',             5,  5,  38000.00, true, now(), now(), 0),
  ('Cabana Isla de Maipo',     'CABANA', 'Isla de Maipo',         6,  6,  52000.00, true, now(), now(), 0),

  -- Lodges (montaña / cordillera)
  ('Lodge Farellones',         'LODGE',  'Farellones',            8,  8, 120000.00, true, now(), now(), 0),
  ('Lodge Valle Nevado',       'LODGE',  'Lo Barnechea',         10, 10, 150000.00, true, now(), now(), 0),
  ('Lodge La Parva',           'LODGE',  'Lo Barnechea',          6,  6, 130000.00, true, now(), now(), 0),
  ('Lodge El Colorado',        'LODGE',  'Farellones',            8,  8, 110000.00, true, now(), now(), 0),
  ('Lodge Cajon Aventura',     'LODGE',  'San Jose de Maipo',     6,  6,  95000.00, true, now(), now(), 0),

  -- Una inactiva, para probar el filtro active=false
  ('Hostal Talagante',         'HOSTAL', 'Talagante',            10, 10,  17000.00, false, now(), now(), 0);

-- Verificar:
--   SELECT id, name, type, available_slots, price_per_night, active FROM units ORDER BY id;
