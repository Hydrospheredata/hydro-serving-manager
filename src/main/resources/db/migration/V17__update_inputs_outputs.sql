update hydro_serving.model_version
set model_signature = REPLACE(model_signature::text, '"dim":', '"dims":')::json;

update hydro_serving.model_version
set model_signature = generateNewSignature(model_signature)

