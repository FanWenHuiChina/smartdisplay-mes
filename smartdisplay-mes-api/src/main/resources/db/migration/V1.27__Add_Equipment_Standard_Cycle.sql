CREATE TABLE IF NOT EXISTS equipment_standard_cycle (
    id BIGSERIAL PRIMARY KEY,
    cycle_no VARCHAR(50) NOT NULL UNIQUE,
    product_code VARCHAR(50) NOT NULL,
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50) NOT NULL,
    recipe_code VARCHAR(50) NOT NULL DEFAULT '',
    cycle_version VARCHAR(30) NOT NULL DEFAULT 'V1.0',
    standard_cycle_seconds DECIMAL(18,3) NOT NULL,
    lower_cycle_seconds DECIMAL(18,3),
    upper_cycle_seconds DECIMAL(18,3),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    effective_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time TIMESTAMP,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_std_cycle_equipment ON equipment_standard_cycle(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_std_cycle_product_step ON equipment_standard_cycle(product_code, step_code);
CREATE INDEX IF NOT EXISTS idx_equipment_std_cycle_status ON equipment_standard_cycle(status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_equipment_std_cycle_active
    ON equipment_standard_cycle(product_code, step_code, equipment_code, recipe_code)
    WHERE status = 'ACTIVE' AND expire_time IS NULL;

INSERT INTO equipment_standard_cycle (
    cycle_no, product_code, step_code, equipment_code, recipe_code, cycle_version,
    standard_cycle_seconds, lower_cycle_seconds, upper_cycle_seconds, status,
    created_by, updated_by, request_snapshot
) VALUES
('ESC-260606-001', 'AMOLED_65', 'COATING', 'COATER_01', 'RCP_COAT_001', 'V1.0',
 58.000, 52.000, 70.000, 'ACTIVE', 'system', 'system', '{"source":"seed-standard-cycle"}'),
('ESC-260606-002', 'AMOLED_67', 'COATING', 'COATER_02', 'RCP_COAT_002', 'V1.0',
 58.000, 52.000, 70.000, 'ACTIVE', 'system', 'system', '{"source":"seed-standard-cycle"}'),
('ESC-260606-003', 'AMOLED_65', 'EVAPORATION', 'EVAP_01', 'RCP_EVAP_001', 'V1.0',
 420.000, 390.000, 480.000, 'ACTIVE', 'system', 'system', '{"source":"seed-standard-cycle"}'),
('ESC-260606-004', 'AMOLED_65', 'INSPECTION', 'INSPECT_01', '', 'V1.0',
 35.000, 30.000, 45.000, 'ACTIVE', 'system', 'system', '{"source":"seed-standard-cycle"}')
ON CONFLICT (cycle_no) DO NOTHING;
