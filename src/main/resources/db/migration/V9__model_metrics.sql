CREATE TABLE hydro_serving.metric_specs(
    id varchar not null primary key,
    kind varchar not null,
    name varchar not null,
    modelVersionId int not null,
    config json
);