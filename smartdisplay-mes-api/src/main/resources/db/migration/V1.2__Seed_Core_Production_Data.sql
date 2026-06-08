INSERT INTO md_recipe (recipe_code, recipe_name, product_code, step_code, equipment_code, recipe_version, status, description, created_by)
VALUES
('RCP_COAT_001', 'AMOLED 6.5寸涂胶Recipe', 'AMOLED_65', 'COATING', 'COATER_01', 'V1.0', 'ACTIVE', '用于AMOLED 6.5寸产品的PI涂胶工序标准参数', 'system'),
('RCP_COAT_002', 'AMOLED 6.7寸涂胶Recipe', 'AMOLED_67', 'COATING', 'COATER_02', 'V1.0', 'ACTIVE', '用于AMOLED 6.7寸柔性屏PI涂胶工序', 'system'),
('RCP_EVAP_001', 'AMOLED蒸镀Recipe', 'AMOLED_65', 'EVAPORATION', 'EVAP_01', 'V2.1', 'ACTIVE', 'RGB有机材料蒸镀参数', 'system'),
('RCP_ETCH_001', '蚀刻标准Recipe', 'AMOLED_65', 'ETCH', 'ETCH_01', 'V1.5', 'ACTIVE', 'Array蚀刻工序参数', 'system'),
('RCP_COAT_OLD', '旧版涂胶Recipe', 'AMOLED_65', 'COATING', 'COATER_01', 'V0.9', 'INACTIVE', '已废弃的旧版本', 'system')
ON CONFLICT (recipe_code) DO NOTHING;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT r.id, item.param_name, item.param_code, item.target_value, item.upper_limit, item.lower_limit, item.unit, item.param_type, item.is_key_param, item.display_order
FROM md_recipe r
JOIN (
    VALUES
    ('RCP_COAT_001', '涂胶温度', 'TEMP_COATING', 150.0, 155.0, 145.0, '℃', 'TEMPERATURE', 1, 1),
    ('RCP_COAT_001', '涂胶速度', 'SPEED_COATING', 300.0, 320.0, 280.0, 'mm/s', 'SPEED', 1, 2),
    ('RCP_COAT_001', '涂胶厚度', 'THICKNESS', 2.0, 2.2, 1.8, 'μm', 'DIMENSION', 1, 3),
    ('RCP_EVAP_001', '蒸镀温度', 'TEMP_EVAP', 280.0, 285.0, 275.0, '℃', 'TEMPERATURE', 1, 1),
    ('RCP_EVAP_001', '真空度', 'VACUUM', 0.0001, 0.0005, 0.00001, 'Pa', 'PRESSURE', 1, 2)
) AS item(recipe_code, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
ON r.recipe_code = item.recipe_code
WHERE NOT EXISTS (
    SELECT 1 FROM md_recipe_param p
    WHERE p.recipe_id = r.id AND p.param_code = item.param_code
);

INSERT INTO md_process_step (step_code, step_name, segment, need_recipe, need_qc) VALUES
('CLEAN', '清洗', 'Array', 1, 0),
('COATING', 'PI涂胶', 'Array', 1, 0),
('EXPOSURE', '曝光', 'Array', 1, 0),
('DEVELOP', '显影', 'Array', 1, 0),
('ETCH', '蚀刻', 'Array', 1, 0),
('STRIP', '剥膜', 'Array', 1, 0),
('EVAPORATION', '蒸镀', 'Cell', 1, 1),
('ENCAPSULATION', '封装', 'Cell', 1, 1),
('INSPECTION', 'AOI检测', 'Cell', 0, 1),
('AGING', '老化', 'Module', 1, 0)
ON CONFLICT (step_code) DO NOTHING;

INSERT INTO md_equipment (equipment_code, equipment_name, equipment_type, line_code, status, capability_steps) VALUES
('COATER_01', '涂胶机-1', 'Coater', 'LINE_01', 'IDLE', '["COATING"]'),
('COATER_02', '涂胶机-2', 'Coater', 'LINE_01', 'RUNNING', '["COATING"]'),
('EVAP_01', '蒸镀机-1', 'Evaporator', 'LINE_01', 'RUNNING', '["EVAPORATION"]'),
('INSPECT_01', 'AOI检测机-1', 'AOI', 'LINE_01', 'IDLE', '["INSPECTION"]'),
('ETCH_01', '蚀刻机-1', 'Etcher', 'LINE_01', 'ALARM', '["ETCH"]')
ON CONFLICT (equipment_code) DO NOTHING;

INSERT INTO prod_order (order_no, product_code, product_name, planned_qty, line_code, status, created_by)
VALUES
('MO202406001', 'AMOLED_65', 'AMOLED 6.5寸柔性屏', 1000, 'LINE_01', 'RELEASED', 'system'),
('MO202406002', 'AMOLED_67', 'AMOLED 6.7寸柔性屏', 800, 'LINE_01', 'RELEASED', 'system')
ON CONFLICT (order_no) DO NOTHING;

INSERT INTO prod_lot (lot_no, order_no, product_code, qty, current_step_code, current_equipment_code, status, hold_flag, created_by)
VALUES
('LOT202406001', 'MO202406001', 'AMOLED_65', 100, 'COATING', NULL, 'READY', 0, 'system'),
('LOT202406002', 'MO202406001', 'AMOLED_65', 100, 'COATING', NULL, 'READY', 0, 'system'),
('LOT202406003', 'MO202406001', 'AMOLED_65', 100, 'COATING', 'COATER_02', 'PROCESSING', 0, 'system'),
('LOT202406004', 'MO202406001', 'AMOLED_65', 100, 'EVAPORATION', 'EVAP_01', 'PROCESSING', 0, 'system'),
('LOT202406005', 'MO202406001', 'AMOLED_65', 100, 'ETCH', NULL, 'HOLD', 1, 'system'),
('LOT202406006', 'MO202406002', 'AMOLED_67', 80, 'COATING', NULL, 'HOLD', 1, 'system'),
('LOT202406007', 'MO202406001', 'AMOLED_65', 100, 'AGING', NULL, 'COMPLETED', 0, 'system'),
('LOT202406008', 'MO202406001', 'AMOLED_65', 100, 'AGING', NULL, 'COMPLETED', 0, 'system'),
('LOT202406009', 'MO202406002', 'AMOLED_67', 80, 'AGING', NULL, 'COMPLETED', 0, 'system'),
('LOT202406010', 'MO202406001', 'AMOLED_65', 100, 'COATING', NULL, 'READY', 0, 'system')
ON CONFLICT (lot_no) DO NOTHING;
