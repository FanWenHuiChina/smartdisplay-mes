CREATE TABLE IF NOT EXISTS material_location_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(80) NOT NULL UNIQUE,
    task_type VARCHAR(20) NOT NULL,
    batch_no VARCHAR(80) NOT NULL,
    material_code VARCHAR(80) NOT NULL,
    material_name VARCHAR(120),
    source_location VARCHAR(60),
    target_location VARCHAR(60),
    planned_qty NUMERIC(18, 6) NOT NULL DEFAULT 0,
    actual_qty NUMERIC(18, 6) NOT NULL DEFAULT 0,
    unit VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'DONE',
    reason VARCHAR(500),
    operator VARCHAR(80),
    executed_time TIMESTAMP,
    request_snapshot TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_location_task_batch
ON material_location_task(batch_no, executed_time DESC);

CREATE INDEX IF NOT EXISTS idx_material_location_task_status
ON material_location_task(status, task_type, executed_time DESC);

INSERT INTO material_location_task (
    task_no, task_type, batch_no, material_code, material_name,
    source_location, target_location, planned_qty, actual_qty, unit,
    status, reason, operator, executed_time, request_snapshot
)
VALUES
('MLT-SEED-260608-001', 'PUTAWAY', 'PI260606-A', 'MAT-PI-001', 'PI胶',
 'WMS-IN', 'WMS-A01', 820.000000, 820.000000, 'g',
 'DONE', '试点种子数据：来料上架', 'wms1001', CURRENT_TIMESTAMP,
 '{"source":"seed","taskType":"PUTAWAY"}'),
('MLT-SEED-260608-002', 'COUNT', 'OLED-R-260605-B', 'MAT-OLED-R', '红光有机材料',
 'COLD-02', 'COLD-02', 310.000000, 310.000000, 'g',
 'DONE', '试点种子数据：低温库盘点', 'wms1001', CURRENT_TIMESTAMP,
 '{"source":"seed","taskType":"COUNT"}')
ON CONFLICT (task_no) DO NOTHING;
