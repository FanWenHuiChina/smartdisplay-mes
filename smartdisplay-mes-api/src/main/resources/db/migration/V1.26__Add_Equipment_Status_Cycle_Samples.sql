CREATE TABLE IF NOT EXISTS equipment_status_history (
    id BIGSERIAL PRIMARY KEY,
    history_no VARCHAR(50) NOT NULL UNIQUE,
    equipment_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    from_status VARCHAR(20),
    to_status VARCHAR(20) NOT NULL,
    change_reason VARCHAR(200),
    source_system VARCHAR(50) NOT NULL DEFAULT 'eap-adapter',
    changed_by VARCHAR(50),
    changed_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_status_hist_equipment ON equipment_status_history(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_status_hist_time ON equipment_status_history(changed_time);
CREATE INDEX IF NOT EXISTS idx_equipment_status_hist_to_status ON equipment_status_history(to_status);

CREATE TABLE IF NOT EXISTS equipment_cycle_sample (
    id BIGSERIAL PRIMARY KEY,
    sample_no VARCHAR(50) NOT NULL UNIQUE,
    equipment_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    lot_no VARCHAR(50),
    step_code VARCHAR(50),
    recipe_code VARCHAR(50),
    standard_cycle_seconds DECIMAL(18,3) NOT NULL,
    actual_cycle_seconds DECIMAL(18,3) NOT NULL,
    output_qty INT NOT NULL DEFAULT 1,
    good_qty INT NOT NULL DEFAULT 1,
    result VARCHAR(20) NOT NULL DEFAULT 'OK',
    sample_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_system VARCHAR(50) NOT NULL DEFAULT 'eap-adapter',
    raw_payload TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_equipment_cycle_equipment ON equipment_cycle_sample(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_cycle_time ON equipment_cycle_sample(sample_time);
CREATE INDEX IF NOT EXISTS idx_equipment_cycle_result ON equipment_cycle_sample(result);

INSERT INTO equipment_status_history (
    history_no, equipment_code, line_code, from_status, to_status, change_reason,
    source_system, changed_by, changed_time, request_snapshot
) VALUES
('ESH-260606-001', 'COATER_01', 'LINE_01', 'PM', 'IDLE', 'PM completed and ready for dispatch',
 'eap-adapter', 'system', CURRENT_TIMESTAMP - INTERVAL '4 hours 25 minutes', '{"source":"seed-status"}'),
('ESH-260606-002', 'ETCH_01', 'LINE_01', 'DOWN', 'ALARM', 'Chamber pressure alarm after stop recovery',
 'eap-adapter', 'system', CURRENT_TIMESTAMP - INTERVAL '2 hours 32 minutes', '{"source":"seed-status"}'),
('ESH-260606-003', 'EVAP_01', 'LINE_01', 'RUNNING', 'DOWN', 'Vacuum pump down',
 'eap-adapter', 'system', CURRENT_TIMESTAMP - INTERVAL '38 minutes', '{"source":"seed-status"}')
ON CONFLICT (history_no) DO NOTHING;

INSERT INTO equipment_cycle_sample (
    sample_no, equipment_code, line_code, lot_no, step_code, recipe_code,
    standard_cycle_seconds, actual_cycle_seconds, output_qty, good_qty, result,
    sample_time, source_system, raw_payload
) VALUES
('ECS-260606-001', 'COATER_01', 'LINE_01', 'LOT202406001', 'COATING', 'RCP_COAT_001',
 58.000, 61.000, 1, 1, 'OK', CURRENT_TIMESTAMP - INTERVAL '55 minutes', 'eap-adapter',
 '{"standardCycleSeconds":58,"actualCycleSeconds":61,"outputQty":1,"goodQty":1}'),
('ECS-260606-002', 'COATER_02', 'LINE_01', 'LOT202406003', 'COATING', 'RCP_COAT_002',
 58.000, 72.000, 1, 0, 'NG', CURRENT_TIMESTAMP - INTERVAL '45 minutes', 'eap-adapter',
 '{"standardCycleSeconds":58,"actualCycleSeconds":72,"outputQty":1,"goodQty":0}'),
('ECS-260606-003', 'EVAP_01', 'LINE_01', 'LOT202406004', 'EVAPORATION', 'RCP_EVAP_001',
 420.000, 455.000, 1, 1, 'WARN', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'eap-adapter',
 '{"standardCycleSeconds":420,"actualCycleSeconds":455,"outputQty":1,"goodQty":1}')
ON CONFLICT (sample_no) DO NOTHING;
