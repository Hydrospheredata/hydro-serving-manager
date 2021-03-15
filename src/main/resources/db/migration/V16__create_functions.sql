create or replace function generateNewDims(IN dims jsonb, OUT new_dims jsonb)
as $$
	select coalesce(jsonb_agg(coalesce(el -> 'size', el)), '[]'::jsonb)
	from jsonb_array_elements(dims::jsonb) with ordinality arr(el)
$$ language sql;

create or replace function generateNewFields(IN fields json, OUT new_fields jsonb)
as $$
	select jsonb_agg(jsonb_set(field,'{shape, dims}', generateNewDims(field -> 'shape' -> 'dims')))
	from jsonb_array_elements(fields::jsonb) with ordinality arr(field)
$$ language sql;

create or replace function generateNewSignature(IN signature json, out new_signature json)
as $$
	select json_build_object(
		'signatureName', signature -> 'signatureName',
		'outputs', generateNewFields(signature -> 'outputs'),
		'inputs', generateNewFields(signature -> 'inputs')
	)
$$ language sql;

create or replace function generateNewStages(IN stages json, OUT new_stages json)
as $$
  select json_agg(json_build_object(
			'variants', stage_json -> 'variants',
			'signature', generateNewSignature(stage_json -> 'signature')
		)
	)
	from json_array_elements(stages::json) with ordinality arr(stage_json)
$$ language sql;