CREATE TABLE IF NOT EXISTS material_incoming_inspection (
    id BIGSERIAL PRIMARY KEY,
    inspection_no VARCHAR(80) NOT NULL UNIQUE,
    batch_no VARCHAR(60) NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100),
    supplier_code VARCHAR(50),
    result VARCHAR(20) NOT NULL,
    inspected_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    sample_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    unit VARCHAR(20),
    defect_code VARCHAR(50),
    defect_description VARCHAR(500),
    coa_no VARCHAR(80),
    conclusion VARCHAR(800),
    inspector VARCHAR(80),
    inspection_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source_system VARCHAR(80) NOT NULL DEFAULT 'qms-adapter',
    request_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_iqc_batch_time
ON material_incoming_inspection(batch_no, inspection_time DESC);

CREATE INDEX IF NOT EXISTS idx_material_iqc_result_time
ON material_incoming_inspection(result, inspection_time DESC);

CREATE TABLE IF NOT EXISTS material_coa_attachment (
    id BIGSERIAL PRIMARY KEY,
    attachment_no VARCHAR(80) NOT NULL UNIQUE,
    inspection_no VARCHAR(80) NOT NULL,
    batch_no VARCHAR(60) NOT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_url VARCHAR(500),
    file_hash VARCHAR(160),
    file_type VARCHAR(40),
    uploaded_by VARCHAR(80),
    uploaded_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_coa_inspection
ON material_coa_attachment(inspection_no);

CREATE INDEX IF NOT EXISTS idx_material_coa_batch
ON material_coa_attachment(batch_no);

INSERT INTO material_incoming_inspection (
    inspection_no, batch_no, material_code, material_name, supplier_code, result,
    inspected_qty, sample_qty, unit, defect_code, defect_description, coa_no,
    conclusion, inspector, inspection_time, source_system, request_snapshot
)
VALUES
('MIQC-SEED-PI260606-A', 'PI260606-A', 'MAT-PI-001', 'PI胶', 'SUP-PI-01', 'PASS',
 80.000000, 5.000000, 'g', NULL, NULL, 'COA-PI260606-A',
 '来料黏度、固含量与外观复核通过。', 'qe1003', CURRENT_TIMESTAMP - INTERVAL '18 hours', 'qms-adapter', '{}'),
('MIQC-SEED-OLED-R-260605-B', 'OLED-R-260605-B', 'MAT-OLED-R', '红光有机材料', 'SUP-OLED-02', 'PASS',
 50.000000, 3.000000, 'g', NULL, NULL, 'COA-OLED-R-260605-B',
 'COA 参数与抽检结果一致。', 'qe1003', CURRENT_TIMESTAMP - INTERVAL '28 hours', 'qms-adapter', '{}')
ON CONFLICT (inspection_no) DO NOTHING;

INSERT INTO material_coa_attachment (
    attachment_no, inspection_no, batch_no, file_name, file_url, file_hash,
    file_type, uploaded_by, uploaded_time
)
VALUES
('MCA-SEED-PI260606-A', 'MIQC-SEED-PI260606-A', 'PI260606-A',
 'COA-PI260606-A.pdf', 'qms://coa/COA-PI260606-A.pdf', 'sha256:seed-pi260606-a',
 'COA', 'qe1003', CURRENT_TIMESTAMP - INTERVAL '18 hours'),
('MCA-SEED-OLED-R-260605-B', 'MIQC-SEED-OLED-R-260605-B', 'OLED-R-260605-B',
 'COA-OLED-R-260605-B.pdf', 'qms://coa/COA-OLED-R-260605-B.pdf', 'sha256:seed-oled-r-260605-b',
 'COA', 'qe1003', CURRENT_TIMESTAMP - INTERVAL '28 hours')
ON CONFLICT (attachment_no) DO NOTHING;
