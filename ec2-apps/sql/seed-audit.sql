-- ============================================================
-- Datos de prueba para auditoria (base: audit_db, tabla: audit_events)
--
-- Reconstruye "a mano" la linea de tiempo de eventos que en produccion
-- llegaria por Kafka (topico reservations.events) para las reservas de
-- seed-reservations.sql: un "reservation.created" por cada reserva, mas un
-- "reservation.status_changed" por cada transicion de estado que tuvo.
--
-- Requiere haber corrido antes seed-reservations.sql contra reservations_db
-- (los reservation_id/unit_id deben existir alla). Ejecutar UNA sola vez.
--
-- Como ejecutarlo en la EC2 (dentro de ec2-apps):
--   docker exec -i andesstay-postgres psql -U andesstay -d audit_db < seed-audit.sql
-- ============================================================

INSERT INTO audit_events
  (event_id, type, reservation_id, unit_id, guest_id, status, occurred_at, recorded_at)
VALUES
  -- reservation.created (una por cada una de las 15 reservas)
  ('evt-r1-created',  'reservation.created', 1,  1,  'u-201', 'CREADA', '2026-09-27 09:00:00-04', '2026-09-27 09:00:02-04'),
  ('evt-r2-created',  'reservation.created', 2,  3,  'u-202', 'CREADA', '2026-09-28 11:30:00-04', '2026-09-28 11:30:02-04'),
  ('evt-r3-created',  'reservation.created', 3,  7,  'u-203', 'CREADA', '2026-09-29 08:15:00-04', '2026-09-29 08:15:02-04'),
  ('evt-r4-created',  'reservation.created', 4,  2,  'u-204', 'CREADA', '2026-09-20 10:00:00-04', '2026-09-20 10:00:02-04'),
  ('evt-r5-created',  'reservation.created', 5,  9,  'u-205', 'CREADA', '2026-09-21 14:20:00-04', '2026-09-21 14:20:02-04'),
  ('evt-r6-created',  'reservation.created', 6,  15, 'u-206', 'CREADA', '2026-09-22 16:45:00-04', '2026-09-22 16:45:02-04'),
  ('evt-r7-created',  'reservation.created', 7,  16, 'u-207', 'CREADA', '2026-09-23 12:10:00-04', '2026-09-23 12:10:02-04'),
  ('evt-r8-created',  'reservation.created', 8,  4,  'u-208', 'CREADA', '2026-09-18 09:30:00-04', '2026-09-18 09:30:02-04'),
  ('evt-r9-created',  'reservation.created', 9,  10, 'u-209', 'CREADA', '2026-09-19 15:00:00-04', '2026-09-19 15:00:02-04'),
  ('evt-r10-created', 'reservation.created', 10, 5,  'u-210', 'CREADA', '2026-09-10 09:00:00-04', '2026-09-10 09:00:02-04'),
  ('evt-r11-created', 'reservation.created', 11, 11, 'u-211', 'CREADA', '2026-09-12 10:15:00-04', '2026-09-12 10:15:02-04'),
  ('evt-r12-created', 'reservation.created', 12, 6,  'u-212', 'CREADA', '2026-08-28 09:00:00-04', '2026-08-28 09:00:02-04'),
  ('evt-r13-created', 'reservation.created', 13, 12, 'u-213', 'CREADA', '2026-09-01 09:00:00-04', '2026-09-01 09:00:02-04'),
  ('evt-r14-created', 'reservation.created', 14, 8,  'u-214', 'CREADA', '2026-09-24 09:00:00-04', '2026-09-24 09:00:02-04'),
  ('evt-r15-created', 'reservation.created', 15, 17, 'u-215', 'CREADA', '2026-09-15 09:00:00-04', '2026-09-15 09:00:02-04'),

  -- reservation.status_changed: reservas 4-7 quedaron en CONFIRMADA
  ('evt-r4-confirmada',  'reservation.status_changed', 4, 2,  'u-204', 'CONFIRMADA', '2026-09-21 09:00:00-04', '2026-09-21 09:00:02-04'),
  ('evt-r5-confirmada',  'reservation.status_changed', 5, 9,  'u-205', 'CONFIRMADA', '2026-09-22 10:00:00-04', '2026-09-22 10:00:02-04'),
  ('evt-r6-confirmada',  'reservation.status_changed', 6, 15, 'u-206', 'CONFIRMADA', '2026-09-23 08:30:00-04', '2026-09-23 08:30:02-04'),
  ('evt-r7-confirmada',  'reservation.status_changed', 7, 16, 'u-207', 'CONFIRMADA', '2026-09-24 09:40:00-04', '2026-09-24 09:40:02-04'),

  -- reserva 8: CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE
  ('evt-r8-confirmada',        'reservation.status_changed', 8, 4, 'u-208', 'CONFIRMADA',        '2026-09-19 10:00:00-04', '2026-09-19 10:00:02-04'),
  ('evt-r8-checkin-pendiente', 'reservation.status_changed', 8, 4, 'u-208', 'CHECKIN_PENDIENTE', '2026-09-28 09:00:00-04', '2026-09-28 09:00:02-04'),

  -- reserva 9: CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE
  ('evt-r9-confirmada',        'reservation.status_changed', 9, 10, 'u-209', 'CONFIRMADA',        '2026-09-20 09:00:00-04', '2026-09-20 09:00:02-04'),
  ('evt-r9-checkin-pendiente', 'reservation.status_changed', 9, 10, 'u-209', 'CHECKIN_PENDIENTE', '2026-09-28 15:00:00-04', '2026-09-28 15:00:02-04'),

  -- reserva 10: CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE -> EN_ESTADIA
  ('evt-r10-confirmada',        'reservation.status_changed', 10, 5, 'u-210', 'CONFIRMADA',        '2026-09-11 09:00:00-04', '2026-09-11 09:00:02-04'),
  ('evt-r10-checkin-pendiente', 'reservation.status_changed', 10, 5, 'u-210', 'CHECKIN_PENDIENTE', '2026-09-24 09:00:00-04', '2026-09-24 09:00:02-04'),
  ('evt-r10-en-estadia',        'reservation.status_changed', 10, 5, 'u-210', 'EN_ESTADIA',        '2026-09-25 12:00:00-04', '2026-09-25 12:00:02-04'),

  -- reserva 11: CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE -> EN_ESTADIA
  ('evt-r11-confirmada',        'reservation.status_changed', 11, 11, 'u-211', 'CONFIRMADA',        '2026-09-13 09:00:00-04', '2026-09-13 09:00:02-04'),
  ('evt-r11-checkin-pendiente', 'reservation.status_changed', 11, 11, 'u-211', 'CHECKIN_PENDIENTE', '2026-09-26 09:00:00-04', '2026-09-26 09:00:02-04'),
  ('evt-r11-en-estadia',        'reservation.status_changed', 11, 11, 'u-211', 'EN_ESTADIA',        '2026-09-27 12:30:00-04', '2026-09-27 12:30:02-04'),

  -- reserva 12: CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE -> EN_ESTADIA -> CHECKOUT
  ('evt-r12-confirmada',        'reservation.status_changed', 12, 6, 'u-212', 'CONFIRMADA',        '2026-08-29 09:00:00-04', '2026-08-29 09:00:02-04'),
  ('evt-r12-checkin-pendiente', 'reservation.status_changed', 12, 6, 'u-212', 'CHECKIN_PENDIENTE', '2026-09-09 09:00:00-04', '2026-09-09 09:00:02-04'),
  ('evt-r12-en-estadia',        'reservation.status_changed', 12, 6, 'u-212', 'EN_ESTADIA',        '2026-09-10 12:00:00-04', '2026-09-10 12:00:02-04'),
  ('evt-r12-checkout',          'reservation.status_changed', 12, 6, 'u-212', 'CHECKOUT',          '2026-09-15 11:00:00-04', '2026-09-15 11:00:02-04'),

  -- reserva 13: CREADA -> CONFIRMADA -> CHECKIN_PENDIENTE -> EN_ESTADIA -> CHECKOUT
  ('evt-r13-confirmada',        'reservation.status_changed', 13, 12, 'u-213', 'CONFIRMADA',        '2026-09-02 09:00:00-04', '2026-09-02 09:00:02-04'),
  ('evt-r13-checkin-pendiente', 'reservation.status_changed', 13, 12, 'u-213', 'CHECKIN_PENDIENTE', '2026-09-14 09:00:00-04', '2026-09-14 09:00:02-04'),
  ('evt-r13-en-estadia',        'reservation.status_changed', 13, 12, 'u-213', 'EN_ESTADIA',        '2026-09-15 12:00:00-04', '2026-09-15 12:00:02-04'),
  ('evt-r13-checkout',          'reservation.status_changed', 13, 12, 'u-213', 'CHECKOUT',          '2026-09-20 11:00:00-04', '2026-09-20 11:00:02-04'),

  -- reserva 14: CREADA -> CANCELADA (nunca llego a confirmar, nunca tomo cupo)
  ('evt-r14-cancelada', 'reservation.status_changed', 14, 8, 'u-214', 'CANCELADA', '2026-09-25 09:00:00-04', '2026-09-25 09:00:02-04'),

  -- reserva 15: CREADA -> CONFIRMADA -> CANCELADA (se devolvio el cupo)
  ('evt-r15-confirmada', 'reservation.status_changed', 15, 17, 'u-215', 'CONFIRMADA', '2026-09-16 09:00:00-04', '2026-09-16 09:00:02-04'),
  ('evt-r15-cancelada',  'reservation.status_changed', 15, 17, 'u-215', 'CANCELADA',  '2026-09-19 09:00:00-04', '2026-09-19 09:00:02-04');

-- Verificar:
--   SELECT reservation_id, type, status, occurred_at FROM audit_events ORDER BY reservation_id, occurred_at;
