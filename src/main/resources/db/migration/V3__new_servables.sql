ALTER TABLE hydro_serving.servable DROP CONSTRAINT servable_pkey;
ALTER TABLE hydro_serving.servable DROP COLUMN service_id;
ALTER TABLE hydro_serving.servable ADD COLUMN status TEXT NOT NULL;
ALTER TABLE hydro_serving.application RENAME COLUMN servables_in_stage TO used_servables;
ALTER TABLE hydro_serving.application ADD COLUMN status_message TEXT;
