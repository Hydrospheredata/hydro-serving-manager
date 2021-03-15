update hydro_serving.application
set execution_graph = replace(execution_graph, '"dim":', '"dims":');

update hydro_serving.application
set execution_graph = json_build_object('stages', generateNewStages(execution_graph::json -> 'stages'))::text