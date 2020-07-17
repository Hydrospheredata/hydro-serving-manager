CREATE TABLE hydro_serving.monitoring_configuration
(
  mc_id   BIGSERIAL PRIMARY KEY,
  batch_size      INT
);

ALTER TABLE hydro_serving.model_version ADD COLUMN monit_conf_id BIGINT REFERENCES monitoring_configuration (mc_id) NOT NULL;


