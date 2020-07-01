ALTER TABLE hydro_serving.model_version DROP COLUMN host_selector;

DELETE TABLE hydro_serving.host_selector;

CREATE TABLE hydro_serving.deployment_configuration(
    name VARCHAR(255) PRIMARY KEY,
    container JSON,
    pod JSON,
    deployment JSON,
    hpa JSON
);