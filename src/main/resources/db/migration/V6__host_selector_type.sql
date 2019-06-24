CREATE EXTENSION IF NOT EXISTS hstore;
ALTER TABLE hydro_serving.host_selector DROP COLUMN placeholder, ADD COLUMN node_selector hstore NOT NULL;