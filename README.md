# hydro-serving-manager
|   |   |   |   |
|---|---|---|---|
|[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=Hydrospheredata_hydro-serving-manager&metric=alert_status)](https://sonarcloud.io/dashboard?id=Hydrospheredata_hydro-serving-manager)|[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=Hydrospheredata_hydro-serving-manager&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=Hydrospheredata_hydro-serving-manager)|[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=Hydrospheredata_hydro-serving-manager&metric=bugs)](https://sonarcloud.io/dashboard?id=Hydrospheredata_hydro-serving-manager)|[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=Hydrospheredata_hydro-serving-manager&metric=code_smells)](https://sonarcloud.io/dashboard?id=Hydrospheredata_hydro-serving-manager)|

## About
Manager service is responsible for:
- registration of models
- deployment and management of models in orchestrator
- entity discovery for other Hydrosphere services

Supports:
- Docker
- Kubernetes

Exposes both GRPC and HTTP API.

## Development
Developer needs `sbt` to build the project.

There are two types of resulting artefacts:
- Compiled jar: `sbt compile`
- Docker image: `sbt docker

## Test
Two types of tests are implemented: unit and integration tests.
To run unit tests: `sbt test`
To run integration tests: `sbt it:test`
To run a specific test case: `sbt testOnly <path_to_test_class>`
