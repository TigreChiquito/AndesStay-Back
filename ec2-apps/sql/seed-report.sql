-- ============================================================
-- Datos de prueba para reporteria (base: report_db, tabla: reservation_projection)
--
-- Read model CQRS: en produccion esta tabla la arma report consumiendo los
-- mismos eventos de Kafka que llenan audit_events (ver seed-audit.sql). Aqui
-- se inserta directamente el estado FINAL de cada reserva de
-- seed-reservations.sql, para tener KPIs de prueba sin depender de Kafka.
--
-- El id (reservation_id) NO es autogenerado: es el mismo id que en
-- reservations_db.reservations, asi que requiere haber corrido antes
-- seed-reservations.sql. Ejecutar UNA sola vez.
--
-- Como ejecutarlo en la EC2 (dentro de ec2-apps):
--   docker exec -i andesstay-postgres psql -U andesstay -d report_db < seed-report.sql
-- ============================================================

INSERT INTO reservation_projection
  (reservation_id, unit_id, check_in_date, check_out_date, status, last_event_at)
VALUES
  (1,  1,  '2026-10-15', '2026-10-18', 'CREADA',            '2026-09-27 09:00:00-04'),
  (2,  3,  '2026-10-20', '2026-10-22', 'CREADA',            '2026-09-28 11:30:00-04'),
  (3,  7,  '2026-11-01', '2026-11-03', 'CREADA',            '2026-09-29 08:15:00-04'),
  (4,  2,  '2026-10-05', '2026-10-08', 'CONFIRMADA',        '2026-09-21 09:00:00-04'),
  (5,  9,  '2026-10-10', '2026-10-12', 'CONFIRMADA',        '2026-09-22 10:00:00-04'),
  (6,  15, '2026-10-18', '2026-10-20', 'CONFIRMADA',        '2026-09-23 08:30:00-04'),
  (7,  16, '2026-10-25', '2026-10-28', 'CONFIRMADA',        '2026-09-24 09:40:00-04'),
  (8,  4,  '2026-09-30', '2026-10-02', 'CHECKIN_PENDIENTE', '2026-09-28 09:00:00-04'),
  (9,  10, '2026-10-01', '2026-10-03', 'CHECKIN_PENDIENTE', '2026-09-28 15:00:00-04'),
  (10, 5,  '2026-09-25', '2026-10-01', 'EN_ESTADIA',        '2026-09-25 12:00:00-04'),
  (11, 11, '2026-09-27', '2026-10-03', 'EN_ESTADIA',        '2026-09-27 12:30:00-04'),
  (12, 6,  '2026-09-10', '2026-09-15', 'CHECKOUT',          '2026-09-15 11:00:00-04'),
  (13, 12, '2026-09-15', '2026-09-20', 'CHECKOUT',          '2026-09-20 11:00:00-04'),
  (14, 8,  '2026-11-10', '2026-11-12', 'CANCELADA',         '2026-09-25 09:00:00-04'),
  (15, 17, '2026-10-08', '2026-10-10', 'CANCELADA',         '2026-09-19 09:00:00-04');

-- Verificar:
--   SELECT status, count(*) FROM reservation_projection GROUP BY status ORDER BY status;
