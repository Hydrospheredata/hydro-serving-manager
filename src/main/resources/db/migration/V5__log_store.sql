CREATE TABLE hydro_serving.build_log (
    version_id BIGINT NOT NULL,
    logs TEXT[] NOT NULL DEFAULT '{}'
);