ALTER TABLE hydro_serving.application ADD COLUMN graph_nodes TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE hydro_serving.application ADD COLUMN graph_links TEXT[] NOT NULL DEFAULT '{}';