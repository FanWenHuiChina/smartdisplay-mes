CREATE TABLE IF NOT EXISTS equipment_recipe_command (
    id BIGSERIAL PRIMARY KEY,
    command_no VARCHAR(50) NOT NULL UNIQUE,
    equipment_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    lot_no VARCHAR(50),
    step_code VARCHAR(50),
    product_code VARCHAR(50),
    recipe_id BIGINT,
    recipe_code VARCHAR(50) NOT NULL,
    recipe_version VARCHAR(20),
    command_type VARCHAR(30) NOT NULL DEFAULT 'DOWNLOAD',
    command_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    download_by VARCHAR(50),
    download_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    eap_ack_status VARCHAR(20) NOT NULL DEFAULT 'ACK',
    readback_status VARCHAR(20) NOT NULL DEFAULT 'MATCH',
    expected_param_snapshot TEXT,
    readback_param_snapshot TEXT,
    mismatch_detail TEXT,
    source_system VARCHAR(50) NOT NULL DEFAULT 'eap-adapter',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_recipe_cmd_equipment ON equipment_recipe_command(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_recipe_cmd_recipe ON equipment_recipe_command(recipe_code);
CREATE INDEX IF NOT EXISTS idx_equipment_recipe_cmd_status ON equipment_recipe_command(command_status, readback_status);
CREATE INDEX IF NOT EXISTS idx_equipment_recipe_cmd_time ON equipment_recipe_command(download_time);

INSERT INTO equipment_recipe_command (
    command_no, equipment_code, line_code, lot_no, step_code, product_code, recipe_id, recipe_code,
    recipe_version, command_type, command_status, download_by, download_time, eap_ack_status,
    readback_status, expected_param_snapshot, readback_param_snapshot, mismatch_detail, source_system
)
SELECT
    'RDL-260606-001', 'COATER_01', 'LINE_01', 'LOT202406001', 'COATING', r.product_code,
    r.id, r.recipe_code, r.recipe_version, 'DOWNLOAD', 'SUCCESS', 'system',
    CURRENT_TIMESTAMP - INTERVAL '50 minutes', 'ACK', 'MATCH',
    '[{"paramCode":"TEMP_COATING","targetValue":150.0},{"paramCode":"SPEED_COATING","targetValue":300.0}]',
    '[{"paramCode":"TEMP_COATING","targetValue":150.0},{"paramCode":"SPEED_COATING","targetValue":300.0}]',
    '', 'eap-adapter'
FROM md_recipe r
WHERE r.recipe_code = 'RCP_COAT_001'
ON CONFLICT (command_no) DO NOTHING;

INSERT INTO equipment_recipe_command (
    command_no, equipment_code, line_code, lot_no, step_code, product_code, recipe_id, recipe_code,
    recipe_version, command_type, command_status, download_by, download_time, eap_ack_status,
    readback_status, expected_param_snapshot, readback_param_snapshot, mismatch_detail, source_system
)
SELECT
    'RDL-260606-002', 'COATER_02', 'LINE_01', 'LOT202406003', 'COATING', r.product_code,
    r.id, r.recipe_code, r.recipe_version, 'DOWNLOAD', 'FAILED', 'system',
    CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'ACK', 'MISMATCH',
    '[{"paramCode":"THICKNESS","targetValue":2.0,"upperLimit":2.2}]',
    '[{"paramCode":"THICKNESS","targetValue":2.3,"upperLimit":2.2}]',
    'THICKNESS targetValue mismatch', 'eap-adapter'
FROM md_recipe r
WHERE r.recipe_code = 'RCP_COAT_002'
ON CONFLICT (command_no) DO NOTHING;
