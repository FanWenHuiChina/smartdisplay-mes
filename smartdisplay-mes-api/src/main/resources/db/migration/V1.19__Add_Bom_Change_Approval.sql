ALTER TABLE md_bom_item
    ADD COLUMN IF NOT EXISTS substitute_priority INT DEFAULT 1,
    ADD COLUMN IF NOT EXISTS substitute_enabled SMALLINT DEFAULT 1;

UPDATE md_bom_item
SET substitute_priority = COALESCE(substitute_priority, 1),
    substitute_enabled = COALESCE(substitute_enabled, 1);

CREATE INDEX IF NOT EXISTS idx_bom_item_substitute_group
ON md_bom_item(bom_id, step_code, substitute_group, substitute_priority);

CREATE TABLE IF NOT EXISTS md_bom_change_request (
    id BIGSERIAL PRIMARY KEY,
    change_no VARCHAR(60) NOT NULL UNIQUE,
    change_type VARCHAR(30) NOT NULL DEFAULT 'VERSION_RELEASE',
    product_code VARCHAR(50) NOT NULL,
    source_bom_code VARCHAR(50),
    target_bom_code VARCHAR(50) NOT NULL,
    target_bom_id BIGINT,
    target_version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    reason VARCHAR(500),
    before_snapshot TEXT,
    after_snapshot TEXT,
    substitute_policy_snapshot TEXT,
    requested_by VARCHAR(50),
    requested_time TIMESTAMP,
    reviewed_by VARCHAR(50),
    reviewed_time TIMESTAMP,
    review_comment VARCHAR(500),
    published_by VARCHAR(50),
    published_time TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_change_target_bom FOREIGN KEY (target_bom_id) REFERENCES md_bom(id)
);

CREATE INDEX IF NOT EXISTS idx_bom_change_product_status
ON md_bom_change_request(product_code, status, requested_time);

CREATE INDEX IF NOT EXISTS idx_bom_change_target_bom
ON md_bom_change_request(target_bom_code);

INSERT INTO md_bom_item (
    bom_id, material_code, material_name, step_code, required_qty, unit,
    is_key_material, substitute_group, substitute_priority, substitute_enabled
)
SELECT bom.id, 'MAT-PI-ALT', 'PI胶替代料', 'COATING', item.required_qty, item.unit,
       1, 'PI', 2, 1
FROM md_bom bom
JOIN md_bom_item item ON item.bom_id = bom.id
WHERE bom.product_code IN ('AMOLED_65', 'AMOLED_67')
  AND item.material_code = 'MAT-PI-001'
ON CONFLICT (bom_id, material_code, step_code) DO NOTHING;

INSERT INTO material_batch (
    material_code, material_name, batch_no, supplier_code, total_qty, available_qty,
    reserved_qty, consumed_qty, unit, status, quality_status, received_time,
    expire_time, location, fifo_seq, created_by, frozen_qty, returned_qty, stock_version
)
VALUES
('MAT-PI-ALT', 'PI胶替代料', 'PI-ALT-260607-A', 'SUP-PI-02', 300.000000, 300.000000, 0.000000, 0.000000, 'g', 'AVAILABLE', 'PASS', CURRENT_TIMESTAMP - INTERVAL '8 hours', CURRENT_TIMESTAMP + INTERVAL '10 days', 'WMS-A-ALT', 50, 'system', 0.000000, 0.000000, 1)
ON CONFLICT (batch_no) DO NOTHING;
