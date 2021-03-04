UPDATE hydro_serving.model_version mv
SET model_signature = json_build_object(
	'signatureName', mv.model_signature -> 'predict' -> 'signatureName',
	'inputs', mv.model_signature -> 'predict' -> 'inputs',
	'outputs', mv.model_signature -> 'predict' -> 'outputs'
)
WHERE mv.model_signature -> 'predict' IS NOT NULL;