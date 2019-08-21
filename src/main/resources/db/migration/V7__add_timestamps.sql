ALTER TABLE hydro_serving.model_version ALTER COLUMN created_timestamp TYPE timestamp with time zone;
ALTER TABLE hydro_serving.model_version ALTER COLUMN finished_timestamp TYPE timestamp with time zone;

--ALTER TABLE hydro_serving.host_selector ADD COLUMN created_timestamp timestamp with time zone NOT NULL;
--ALTER TABLE hydro_serving.host_selector ADD COLUMN updated_timestamp timestamp with time zone NOT NULL;
--
--ALTER TABLE hydro_serving.application ADD COLUMN created_timestamp timestamp with time zone NOT NULL;
--ALTER TABLE hydro_serving.application ADD COLUMN updated_timestamp timestamp with time zone NOT NULL;
--
--ALTER TABLE hydro_serving.servable ADD COLUMN created_timestamp timestamp with time zone NOT NULL;
--ALTER TABLE hydro_serving.servable ADD COLUMN updated_timestamp timestamp with time zone NOT NULL;
