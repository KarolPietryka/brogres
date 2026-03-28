-- Mock: trening 2026-03-20, 2 ćwiczenia (H2 / profil domyślny).
-- Uruchamiane po schemacie Hibernate (spring.jpa.defer-datasource-initialization).

INSERT INTO workout (workout_date, created_by, modified_by, created_on, modified_on)
SELECT DATE '2026-03-20', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM workout WHERE workout_date = DATE '2026-03-20');

INSERT INTO workout_set (exercise, repetitions, body_part, weight, line_order, workout_id, planned)
SELECT 'Wyciskanie sztangi', 8, 'chest', 80.0, 0, w.id, FALSE
FROM workout w
WHERE w.workout_date = DATE '2026-03-20'
  AND NOT EXISTS (SELECT 1 FROM workout_set s WHERE s.workout_id = w.id AND s.line_order = 0);

INSERT INTO workout_set (exercise, repetitions, body_part, weight, line_order, workout_id, planned)
SELECT 'Wiosłowanie hantlem', 10, 'back', 42.0, 1, w.id, FALSE
FROM workout w
WHERE w.workout_date = DATE '2026-03-20'
  AND NOT EXISTS (SELECT 1 FROM workout_set s WHERE s.workout_id = w.id AND s.line_order = 1);
