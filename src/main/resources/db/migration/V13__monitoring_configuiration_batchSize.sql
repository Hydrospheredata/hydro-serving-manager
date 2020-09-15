ALTER TABLE hydro_serving.model_version ALTER COLUMN monitoring_configuration SET DEFAULT '{"batchSize": 10}'::json;
UPDATE hydro_serving.model_version SET monitoring_configuration = '{"batchSize": 10}'::json WHERE monitoring_configuration -> 'batch_size' IS NOT NULL;
