INSERT INTO md_equipment (equipment_code, equipment_name, equipment_type, line_code, status, capability_steps, location, description)
VALUES
('CLEANER_01', 'Cleaner-1', 'Cleaner', 'LINE_01', 'IDLE', '["CLEAN"]', 'Array-Clean', 'Pilot dispatch equipment for CLEAN'),
('EXPOSURE_01', 'Exposure-1', 'Exposure', 'LINE_01', 'IDLE', '["EXPOSURE"]', 'Array-Photo', 'Pilot dispatch equipment for EXPOSURE'),
('ETCH_02', 'Etcher-2', 'Etcher', 'LINE_01', 'IDLE', '["ETCH"]', 'Array-Etch', 'Available etch equipment for route dispatch'),
('ENCAP_01', 'Encapsulation-1', 'Encap', 'LINE_01', 'IDLE', '["ENCAPSULATION"]', 'Cell-Encap', 'Pilot dispatch equipment for ENCAPSULATION'),
('AGING_01', 'Aging-1', 'Aging', 'LINE_01', 'IDLE', '["AGING"]', 'Module-Aging', 'Pilot dispatch equipment for AGING')
ON CONFLICT (equipment_code) DO NOTHING;

INSERT INTO md_recipe (recipe_code, recipe_name, product_code, step_code, equipment_code, recipe_version, status, description, created_by)
VALUES
('RCP_CLEAN_65_V1', 'AMOLED 6.5 CLEAN recipe', 'AMOLED_65', 'CLEAN', 'CLEANER_01', 'V1.0', 'ACTIVE', 'Pilot baseline clean recipe', 'system'),
('RCP_CLEAN_67_V1', 'AMOLED 6.7 CLEAN recipe', 'AMOLED_67', 'CLEAN', 'CLEANER_01', 'V1.0', 'ACTIVE', 'Pilot baseline clean recipe', 'system'),
('RCP_EXPOSURE_65_V1', 'AMOLED 6.5 EXPOSURE recipe', 'AMOLED_65', 'EXPOSURE', 'EXPOSURE_01', 'V1.0', 'ACTIVE', 'Pilot baseline exposure recipe', 'system'),
('RCP_EXPOSURE_67_V1', 'AMOLED 6.7 EXPOSURE recipe', 'AMOLED_67', 'EXPOSURE', 'EXPOSURE_01', 'V1.0', 'ACTIVE', 'Pilot baseline exposure recipe', 'system'),
('RCP_ETCH_65_V2', 'AMOLED 6.5 ETCH recipe', 'AMOLED_65', 'ETCH', 'ETCH_02', 'V2.0', 'ACTIVE', 'Pilot available etch recipe', 'system'),
('RCP_ETCH_67_V1', 'AMOLED 6.7 ETCH recipe', 'AMOLED_67', 'ETCH', 'ETCH_02', 'V1.0', 'ACTIVE', 'Pilot available etch recipe', 'system'),
('RCP_EVAP_67_V1', 'AMOLED 6.7 EVAPORATION recipe', 'AMOLED_67', 'EVAPORATION', 'EVAP_01', 'V1.0', 'ACTIVE', 'Pilot baseline evaporation recipe', 'system'),
('RCP_ENCAP_65_V1', 'AMOLED 6.5 ENCAPSULATION recipe', 'AMOLED_65', 'ENCAPSULATION', 'ENCAP_01', 'V1.0', 'ACTIVE', 'Pilot baseline encapsulation recipe', 'system'),
('RCP_ENCAP_67_V1', 'AMOLED 6.7 ENCAPSULATION recipe', 'AMOLED_67', 'ENCAPSULATION', 'ENCAP_01', 'V1.0', 'ACTIVE', 'Pilot baseline encapsulation recipe', 'system'),
('RCP_INSPECT_65_V1', 'AMOLED 6.5 INSPECTION recipe', 'AMOLED_65', 'INSPECTION', 'INSPECT_01', 'V1.0', 'ACTIVE', 'Pilot baseline inspection recipe', 'system'),
('RCP_INSPECT_67_V1', 'AMOLED 6.7 INSPECTION recipe', 'AMOLED_67', 'INSPECTION', 'INSPECT_01', 'V1.0', 'ACTIVE', 'Pilot baseline inspection recipe', 'system'),
('RCP_AGING_65_V1', 'AMOLED 6.5 AGING recipe', 'AMOLED_65', 'AGING', 'AGING_01', 'V1.0', 'ACTIVE', 'Pilot baseline aging recipe', 'system'),
('RCP_AGING_67_V1', 'AMOLED 6.7 AGING recipe', 'AMOLED_67', 'AGING', 'AGING_01', 'V1.0', 'ACTIVE', 'Pilot baseline aging recipe', 'system')
ON CONFLICT (recipe_code) DO NOTHING;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT r.id, item.param_name, item.param_code, item.target_value, item.upper_limit, item.lower_limit,
       item.unit, item.param_type, item.is_key_param, item.display_order
FROM md_recipe r
JOIN (
    VALUES
    ('RCP_CLEAN_65_V1', 'Clean time', 'CLEAN_TIME', 60.0, 120.0, 30.0, 's', 'TIME', 1, 1),
    ('RCP_CLEAN_67_V1', 'Clean time', 'CLEAN_TIME', 60.0, 120.0, 30.0, 's', 'TIME', 1, 1),
    ('RCP_EXPOSURE_65_V1', 'Exposure dose', 'EXPOSURE_DOSE', 120.0, 130.0, 110.0, 'mJ', 'ENERGY', 1, 1),
    ('RCP_EXPOSURE_67_V1', 'Exposure dose', 'EXPOSURE_DOSE', 120.0, 130.0, 110.0, 'mJ', 'ENERGY', 1, 1),
    ('RCP_ETCH_65_V2', 'Etch time', 'ETCH_TIME', 80.0, 100.0, 60.0, 's', 'TIME', 1, 1),
    ('RCP_ETCH_67_V1', 'Etch time', 'ETCH_TIME', 80.0, 100.0, 60.0, 's', 'TIME', 1, 1),
    ('RCP_EVAP_67_V1', 'Evap temperature', 'TEMP_EVAP', 280.0, 285.0, 275.0, 'C', 'TEMPERATURE', 1, 1),
    ('RCP_ENCAP_65_V1', 'Encap pressure', 'ENCAP_PRESSURE', 0.6, 0.8, 0.4, 'MPa', 'PRESSURE', 1, 1),
    ('RCP_ENCAP_67_V1', 'Encap pressure', 'ENCAP_PRESSURE', 0.6, 0.8, 0.4, 'MPa', 'PRESSURE', 1, 1),
    ('RCP_INSPECT_65_V1', 'AOI sample rate', 'AOI_SAMPLE_RATE', 100.0, 100.0, 1.0, '%', 'QUALITY', 0, 1),
    ('RCP_INSPECT_67_V1', 'AOI sample rate', 'AOI_SAMPLE_RATE', 100.0, 100.0, 1.0, '%', 'QUALITY', 0, 1),
    ('RCP_AGING_65_V1', 'Aging temperature', 'AGING_TEMP', 60.0, 70.0, 50.0, 'C', 'TEMPERATURE', 1, 1),
    ('RCP_AGING_67_V1', 'Aging temperature', 'AGING_TEMP', 60.0, 70.0, 50.0, 'C', 'TEMPERATURE', 1, 1)
) AS item(recipe_code, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
ON r.recipe_code = item.recipe_code
WHERE NOT EXISTS (
    SELECT 1
    FROM md_recipe_param p
    WHERE p.recipe_id = r.id
      AND p.param_code = item.param_code
);
