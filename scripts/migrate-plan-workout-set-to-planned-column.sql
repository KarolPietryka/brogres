-- Jednorazowa migracja Postgres (gdy istnieje stara tabela plan_workout_set).
-- Uruchom ręcznie przed / po deployu; dostosuj nazwy schematu jeśli trzeba.

-- 1) Kolumna na wykonanych seriach
ALTER TABLE workout_set ADD COLUMN IF NOT EXISTS planned BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Przenieś snapshot planu do workout_set (planned = true)
INSERT INTO workout_set (exercise, repetitions, body_part, weight, line_order, workout_id, planned)
SELECT p.exercise, p.repetitions, p.body_part, p.weight, p.line_order, p.workout_id, TRUE
FROM plan_workout_set p
WHERE NOT EXISTS (
    SELECT 1 FROM workout_set s
    WHERE s.workout_id = p.workout_id AND s.line_order = p.line_order AND s.planned = TRUE
);

-- 3) Usuń starą tabelę (po weryfikacji danych)
-- DROP TABLE IF EXISTS plan_workout_set;
