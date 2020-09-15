ALTER TABLE hydro_serving.model_version DROP COLUMN host_selector;

DROP TABLE hydro_serving.host_selector;

CREATE TABLE hydro_serving.deployment_configuration(
    name VARCHAR(255) PRIMARY KEY,
    container JSON,
    pod JSON,
    deployment JSON,
    hpa JSON
);

ALTER TABLE hydro_serving.servable ADD COLUMN deployment_configuration VARCHAR(255) REFERENCES deployment_configuration(name);