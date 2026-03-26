-- H2: przesuń datę każdego treningu o 1 dzień wstecz (kolumna DATE).
-- Uruchom na kopii / w transakcji z testem; przy unikalnym workout_date sprawdź duplikaty po UPDATE.

-- Podgląd przed zmianą:
-- SELECT id, workout_date FROM workout ORDER BY workout_date;

BEGIN;

UPDATE workout
SET workout_date = workout_date - 1;

-- SELECT id, workout_date FROM workout ORDER BY workout_date;

COMMIT;

-- Odwrotność: +1 dzień
-- UPDATE workout SET workout_date = workout_date + 1;

-- Na PostgreSQL (np. Render) zamiast tego:
-- UPDATE workout SET workout_date = workout_date - INTERVAL '1 day';
