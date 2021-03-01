ALTER TABLE hydro_serving.model_version RENAME COLUMN model_contract TO model_signature;

ALTER TABLE hydro_serving.model_version
	ALTER COLUMN model_signature SET DATA TYPE json
	USING model_signature::json