CREATE TABLE hydro_serving.metric_specs(
    id VARCHAR NOT NULL PRIMARY KEY,
    kind VARCHAR NOT NULL,
    name VARCHAR NOT NULL,
    modelVersionId BIGINT NOT NULL,
    config VARCHAR
);