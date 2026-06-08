CREATE TABLE IF NOT EXISTS quality_inspection (
    id BIGSERIAL PRIMARY KEY,
    inspection_no VARCHAR(60) NOT NULL UNIQUE,
    lot_no VARCHAR(50) NOT NULL,
    order_no VARCHAR(50),
    product_code VARCHAR(50),
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50),
    recipe_code VARCHAR(50),
    item_code VARCHAR(80) NOT NULL,
    item_name VARCHAR(120),
    measured_value DECIMAL(18,6),
    upper_limit DECIMAL(18,6),
    lower_limit DECIMAL(18,6),
    unit VARCHAR(20),
    result VARCHAR(20) NOT NULL,
    defect_code VARCHAR(50),
    defect_position VARCHAR(100),
    inspector VARCHAR(50),
    inspection_time TIMESTAMP NOT NULL,
    source VARCHAR(50),
    remark VARCHAR(500),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_inspection_lot ON quality_inspection(lot_no);
CREATE INDEX IF NOT EXISTS idx_quality_inspection_result ON quality_inspection(result);
CREATE INDEX IF NOT EXISTS idx_quality_inspection_time ON quality_inspection(inspection_time);

CREATE TABLE IF NOT EXISTS quality_defect_record (
    id BIGSERIAL PRIMARY KEY,
    defect_no VARCHAR(60) NOT NULL UNIQUE,
    inspection_no VARCHAR(60),
    lot_no VARCHAR(50) NOT NULL,
    step_code VARCHAR(50),
    equipment_code VARCHAR(50),
    defect_code VARCHAR(50) NOT NULL,
    defect_name VARCHAR(100),
    defect_level VARCHAR(20),
    defect_position VARCHAR(100),
    qty INT DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    disposition VARCHAR(80),
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_defect_lot ON quality_defect_record(lot_no);
CREATE INDEX IF NOT EXISTS idx_quality_defect_code ON quality_defect_record(defect_code);
CREATE INDEX IF NOT EXISTS idx_quality_defect_status ON quality_defect_record(status);

CREATE TABLE IF NOT EXISTS exception_event (
    id BIGSERIAL PRIMARY KEY,
    event_no VARCHAR(60) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    event_level VARCHAR(20) NOT NULL,
    lot_no VARCHAR(50),
    order_no VARCHAR(50),
    step_code VARCHAR(50),
    equipment_code VARCHAR(50),
    source_module VARCHAR(50),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    owner_role VARCHAR(50),
    owner_user VARCHAR(50),
    occurred_time TIMESTAMP NOT NULL,
    closed_time TIMESTAMP,
    close_conclusion VARCHAR(500),
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exception_event_lot ON exception_event(lot_no);
CREATE INDEX IF NOT EXISTS idx_exception_event_status ON exception_event(status);
CREATE INDEX IF NOT EXISTS idx_exception_event_time ON exception_event(occurred_time);

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶温度', 'TEMP_COATING', 150.0, 155.0, 145.0, '℃', 'TEMPERATURE', 1, 1
FROM md_recipe
WHERE recipe_code = 'RCP_COAT_002'
  AND NOT EXISTS (
      SELECT 1 FROM md_recipe_param p
      WHERE p.recipe_id = md_recipe.id AND p.param_code = 'TEMP_COATING'
  )
LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶速度', 'SPEED_COATING', 300.0, 320.0, 280.0, 'mm/s', 'SPEED', 1, 2
FROM md_recipe
WHERE recipe_code = 'RCP_COAT_002'
  AND NOT EXISTS (
      SELECT 1 FROM md_recipe_param p
      WHERE p.recipe_id = md_recipe.id AND p.param_code = 'SPEED_COATING'
  )
LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶厚度', 'THICKNESS', 2.0, 2.2, 1.8, 'μm', 'DIMENSION', 1, 3
FROM md_recipe
WHERE recipe_code = 'RCP_COAT_002'
  AND NOT EXISTS (
      SELECT 1 FROM md_recipe_param p
      WHERE p.recipe_id = md_recipe.id AND p.param_code = 'THICKNESS'
  )
LIMIT 1;

INSERT INTO lot_hold_record (lot_no, hold_reason, hold_type, hold_by, hold_time, status)
SELECT item.lot_no, item.hold_reason, item.hold_type, item.hold_by, item.hold_time, item.status
FROM (
    VALUES
    ('LOT202406005', '蚀刻设备报警隔离，等待设备工程师确认', 'EQUIPMENT', 'system', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'HOLD'),
    ('LOT202406006', '涂胶膜厚超限，质量工程师复判中', 'QUALITY', 'system', CURRENT_TIMESTAMP - INTERVAL '75 minutes', 'HOLD')
) AS item(lot_no, hold_reason, hold_type, hold_by, hold_time, status)
WHERE NOT EXISTS (
    SELECT 1 FROM lot_hold_record h
    WHERE h.lot_no = item.lot_no AND h.status = 'HOLD'
);

INSERT INTO quality_inspection (
    inspection_no, lot_no, order_no, product_code, step_code, equipment_code, recipe_code,
    item_code, item_name, measured_value, upper_limit, lower_limit, unit, result,
    defect_code, defect_position, inspector, inspection_time, source, remark
)
VALUES
('QI-SEED-LOT202406006-001', 'LOT202406006', 'MO202406002', 'AMOLED_67', 'COATING', 'COATER_02', 'RCP_COAT_002',
 'THICKNESS', '涂胶厚度', 2.260000, 2.200000, 1.800000, 'μm', 'NG',
 'D-THICKNESS', 'COATING', 'system', CURRENT_TIMESTAMP - INTERVAL '76 minutes', 'SEED', '初始化种子数据：膜厚超上限'),
('QI-SEED-LOT202406004-001', 'LOT202406004', 'MO202406001', 'AMOLED_65', 'EVAPORATION', 'EVAP_01', 'RCP_EVAP_001',
 'VACUUM', '真空度', 0.000620, 0.000500, 0.000010, 'Pa', 'NG',
 'D-VACUUM', 'EVAPORATION', 'system', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'SEED', '初始化种子数据：真空度波动')
ON CONFLICT (inspection_no) DO NOTHING;

INSERT INTO quality_defect_record (
    defect_no, inspection_no, lot_no, step_code, equipment_code, defect_code, defect_name,
    defect_level, defect_position, qty, status, disposition, created_by
)
VALUES
('QD-SEED-LOT202406006-001', 'QI-SEED-LOT202406006-001', 'LOT202406006', 'COATING', 'COATER_02',
 'D-THICKNESS', '涂胶厚度超限', 'MAJOR', 'COATING', 1, 'OPEN', 'WAIT_MRB', 'system'),
('QD-SEED-LOT202406004-001', 'QI-SEED-LOT202406004-001', 'LOT202406004', 'EVAPORATION', 'EVAP_01',
 'D-VACUUM', '真空度超限', 'MAJOR', 'EVAPORATION', 1, 'OPEN', 'WAIT_MRB', 'system')
ON CONFLICT (defect_no) DO NOTHING;

INSERT INTO exception_event (
    event_no, event_type, event_level, lot_no, order_no, step_code, equipment_code,
    source_module, title, description, status, owner_role, occurred_time, created_by
)
VALUES
('EX-SEED-LOT202406006-001', 'QUALITY', 'P1', 'LOT202406006', 'MO202406002', 'COATING', 'COATER_02',
 'QUALITY', '涂胶膜厚超限', 'LOT202406006 在 COATING 工序涂胶厚度判定不合格', 'OPEN', 'QE', CURRENT_TIMESTAMP - INTERVAL '75 minutes', 'system'),
('EX-SEED-EVAP-001', 'EQUIPMENT', 'P2', 'LOT202406004', 'MO202406001', 'EVAPORATION', 'EVAP_01',
 'EAP_ADAPTER', '蒸镀真空度波动', 'EVAP_01 真空度高于 Recipe 上限，需设备工程师确认', 'PROCESSING', 'EE', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'system')
ON CONFLICT (event_no) DO NOTHING;
