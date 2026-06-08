CREATE TABLE IF NOT EXISTS equipment_event (
    id BIGSERIAL PRIMARY KEY,
    event_no VARCHAR(50) NOT NULL UNIQUE,
    equipment_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    event_type VARCHAR(30) NOT NULL,
    event_level VARCHAR(20) NOT NULL DEFAULT 'P2',
    lot_no VARCHAR(50),
    step_code VARCHAR(50),
    recipe_code VARCHAR(50),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    source_system VARCHAR(50) NOT NULL DEFAULT 'eap-adapter',
    occurred_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_by VARCHAR(50),
    closed_time TIMESTAMP,
    close_conclusion VARCHAR(500),
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_event_equipment ON equipment_event(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_event_status ON equipment_event(status);
CREATE INDEX IF NOT EXISTS idx_equipment_event_level ON equipment_event(event_level);
CREATE INDEX IF NOT EXISTS idx_equipment_event_time ON equipment_event(occurred_time);

CREATE TABLE IF NOT EXISTS equipment_pm_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(50) NOT NULL UNIQUE,
    equipment_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    pm_type VARCHAR(50) NOT NULL,
    pm_level VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    plan_start_time TIMESTAMP,
    plan_end_time TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    checklist TEXT,
    result VARCHAR(20),
    operator VARCHAR(50),
    completed_time TIMESTAMP,
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_pm_equipment ON equipment_pm_task(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_pm_status ON equipment_pm_task(status);
CREATE INDEX IF NOT EXISTS idx_equipment_pm_plan_end ON equipment_pm_task(plan_end_time);

CREATE TABLE IF NOT EXISTS equipment_parameter_sample (
    id BIGSERIAL PRIMARY KEY,
    sample_no VARCHAR(50) NOT NULL UNIQUE,
    equipment_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    lot_no VARCHAR(50),
    step_code VARCHAR(50),
    recipe_code VARCHAR(50),
    param_code VARCHAR(50) NOT NULL,
    param_name VARCHAR(100),
    param_value DECIMAL(18,6) NOT NULL,
    unit VARCHAR(20),
    lower_limit DECIMAL(18,6),
    upper_limit DECIMAL(18,6),
    result VARCHAR(20) NOT NULL DEFAULT 'OK',
    sample_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_system VARCHAR(50) NOT NULL DEFAULT 'eap-adapter',
    raw_payload TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_equipment_param_equipment ON equipment_parameter_sample(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_param_time ON equipment_parameter_sample(sample_time);
CREATE INDEX IF NOT EXISTS idx_equipment_param_result ON equipment_parameter_sample(result);

INSERT INTO equipment_event (
    event_no, equipment_code, line_code, event_type, event_level, lot_no, step_code, recipe_code,
    title, description, status, source_system, occurred_time, created_by
) VALUES
('EVT-260606-001', 'EVAP_01', 'LINE_01', 'ALARM', 'P2', 'LOT202406004', 'EVAPORATION', 'RCP_EVAP_001',
 'Vacuum fluctuation', 'Vacuum value drifted near the recipe upper limit.', 'OPEN', 'eap-adapter', CURRENT_TIMESTAMP - INTERVAL '70 minutes', 'system'),
('EVT-260606-002', 'COATER_02', 'LINE_01', 'PARAMETER', 'P1', 'LOT202406003', 'COATING', 'RCP_COAT_002',
 'Coating thickness out of limit', 'EAP reported coating thickness above the recipe upper limit.', 'OPEN', 'eap-adapter', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'system'),
('EVT-260606-003', 'INSPECT_01', 'LINE_01', 'QUALITY', 'P2', 'LOT202406006', 'INSPECTION', NULL,
 'AOI defect trend warning', 'AOI defect count increased for the current shift.', 'PROCESSING', 'qms-adapter', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'system')
ON CONFLICT (event_no) DO NOTHING;

INSERT INTO equipment_pm_task (
    task_no, equipment_code, line_code, pm_type, pm_level, plan_start_time, plan_end_time,
    status, checklist, created_by
) VALUES
('PM-260606-COATER01', 'COATER_01', 'LINE_01', 'NOZZLE_CLEAN', 'SHIFT',
 CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP + INTERVAL '2 hours', 'OPEN',
 '["clean nozzle","check dispense pressure","verify thickness monitor"]', 'system'),
('PM-260606-EVAP01', 'EVAP_01', 'LINE_01', 'VACUUM_CHECK', 'DAILY',
 CURRENT_TIMESTAMP - INTERVAL '4 hours', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'OVERDUE',
 '["check vacuum pump","verify leak rate","record chamber pressure"]', 'system'),
('PM-260606-INSPECT01', 'INSPECT_01', 'LINE_01', 'CAMERA_CALIBRATION', 'WEEKLY',
 CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP - INTERVAL '20 hours', 'COMPLETED',
 '["calibrate camera","verify golden sample","upload calibration report"]', 'system')
ON CONFLICT (task_no) DO NOTHING;

INSERT INTO equipment_parameter_sample (
    sample_no, equipment_code, line_code, lot_no, step_code, recipe_code, param_code, param_name,
    param_value, unit, lower_limit, upper_limit, result, sample_time, source_system, raw_payload
) VALUES
('EPS-260606-001', 'COATER_02', 'LINE_01', 'LOT202406003', 'COATING', 'RCP_COAT_002',
 'THICKNESS', 'Coating thickness', 2.260000, 'um', 1.800000, 2.200000, 'NG', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'eap-adapter',
 '{"paramCode":"THICKNESS","paramValue":2.26,"unit":"um"}'),
('EPS-260606-002', 'EVAP_01', 'LINE_01', 'LOT202406004', 'EVAPORATION', 'RCP_EVAP_001',
 'VACUUM', 'Vacuum', 0.000620, 'Pa', 0.000010, 0.000500, 'NG', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'eap-adapter',
 '{"paramCode":"VACUUM","paramValue":0.00062,"unit":"Pa"}'),
('EPS-260606-003', 'COATER_01', 'LINE_01', 'LOT202406001', 'COATING', 'RCP_COAT_001',
 'TEMP_COATING', 'Coating temperature', 150.200000, 'C', 145.000000, 155.000000, 'OK', CURRENT_TIMESTAMP - INTERVAL '25 minutes', 'eap-adapter',
 '{"paramCode":"TEMP_COATING","paramValue":150.2,"unit":"C"}')
ON CONFLICT (sample_no) DO NOTHING;
