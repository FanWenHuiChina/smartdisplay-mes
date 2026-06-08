CREATE TABLE IF NOT EXISTS md_bom (
    id BIGSERIAL PRIMARY KEY,
    bom_code VARCHAR(50) NOT NULL UNIQUE,
    bom_name VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    bom_version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_time TIMESTAMP,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_product_version
ON md_bom(product_code, bom_version) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_bom_product_status ON md_bom(product_code, status);

CREATE TABLE IF NOT EXISTS md_bom_item (
    id BIGSERIAL PRIMARY KEY,
    bom_id BIGINT NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100) NOT NULL,
    step_code VARCHAR(50) NOT NULL,
    required_qty DECIMAL(18,6) NOT NULL,
    unit VARCHAR(20),
    is_key_material SMALLINT DEFAULT 1,
    substitute_group VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_item_bom FOREIGN KEY (bom_id) REFERENCES md_bom(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_item_material_step ON md_bom_item(bom_id, material_code, step_code);
CREATE INDEX IF NOT EXISTS idx_bom_item_step ON md_bom_item(step_code);
CREATE INDEX IF NOT EXISTS idx_bom_item_material ON md_bom_item(material_code);

CREATE TABLE IF NOT EXISTS material_batch (
    id BIGSERIAL PRIMARY KEY,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100) NOT NULL,
    batch_no VARCHAR(60) NOT NULL UNIQUE,
    supplier_code VARCHAR(50),
    total_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    available_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    reserved_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    consumed_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    unit VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    quality_status VARCHAR(20) NOT NULL DEFAULT 'PASS',
    received_time TIMESTAMP,
    expire_time TIMESTAMP,
    location VARCHAR(100),
    fifo_seq INT DEFAULT 0,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_batch_material ON material_batch(material_code);
CREATE INDEX IF NOT EXISTS idx_material_batch_status ON material_batch(status, quality_status);
CREATE INDEX IF NOT EXISTS idx_material_batch_fifo ON material_batch(material_code, fifo_seq, received_time);

CREATE TABLE IF NOT EXISTS material_loading (
    id BIGSERIAL PRIMARY KEY,
    loading_no VARCHAR(60) NOT NULL UNIQUE,
    lot_no VARCHAR(50) NOT NULL,
    order_no VARCHAR(50),
    product_code VARCHAR(50),
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50),
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100),
    batch_no VARCHAR(60) NOT NULL,
    required_qty DECIMAL(18,6) NOT NULL,
    loaded_qty DECIMAL(18,6) NOT NULL,
    unit VARCHAR(20),
    status VARCHAR(20) NOT NULL DEFAULT 'LOADED',
    operator VARCHAR(50),
    loaded_time TIMESTAMP NOT NULL,
    consumed_time TIMESTAMP,
    remark VARCHAR(300),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_loading_lot ON material_loading(lot_no);
CREATE INDEX IF NOT EXISTS idx_material_loading_status ON material_loading(status);
CREATE INDEX IF NOT EXISTS idx_material_loading_batch ON material_loading(batch_no);

CREATE TABLE IF NOT EXISTS material_consumption (
    id BIGSERIAL PRIMARY KEY,
    consumption_no VARCHAR(60) NOT NULL UNIQUE,
    lot_no VARCHAR(50) NOT NULL,
    order_no VARCHAR(50),
    product_code VARCHAR(50),
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50),
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100),
    batch_no VARCHAR(60) NOT NULL,
    consumed_qty DECIMAL(18,6) NOT NULL,
    unit VARCHAR(20),
    operator VARCHAR(50),
    consume_time TIMESTAMP NOT NULL,
    step_record_id BIGINT,
    trace_status VARCHAR(30) NOT NULL DEFAULT 'TRACEABLE',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_consumption_lot ON material_consumption(lot_no);
CREATE INDEX IF NOT EXISTS idx_material_consumption_batch ON material_consumption(batch_no);
CREATE INDEX IF NOT EXISTS idx_material_consumption_time ON material_consumption(consume_time);

CREATE TABLE IF NOT EXISTS material_carrier (
    id BIGSERIAL PRIMARY KEY,
    carrier_no VARCHAR(60) NOT NULL UNIQUE,
    carrier_type VARCHAR(40),
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    lot_no VARCHAR(50),
    product_code VARCHAR(50),
    step_code VARCHAR(50),
    equipment_code VARCHAR(50),
    bind_time TIMESTAMP,
    unbind_time TIMESTAMP,
    location VARCHAR(100),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_carrier_lot ON material_carrier(lot_no);
CREATE INDEX IF NOT EXISTS idx_material_carrier_status ON material_carrier(status);

INSERT INTO md_bom (bom_code, bom_name, product_code, bom_version, status, effective_time, created_by)
VALUES
('BOM_65_V06', 'AMOLED 6.5寸柔性屏试点BOM', 'AMOLED_65', 'V06', 'ACTIVE', CURRENT_TIMESTAMP, 'system'),
('BOM_67_V04', 'AMOLED 6.7寸柔性屏试点BOM', 'AMOLED_67', 'V04', 'ACTIVE', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (bom_code) DO NOTHING;

INSERT INTO md_bom_item (bom_id, material_code, material_name, step_code, required_qty, unit, is_key_material, substitute_group)
SELECT bom.id, item.material_code, item.material_name, item.step_code, item.required_qty, item.unit, item.is_key_material, item.substitute_group
FROM md_bom bom
JOIN (
    VALUES
    ('BOM_65_V06', 'MAT-PI-001', 'PI胶', 'COATING', 0.420000, 'g', 1, 'PI'),
    ('BOM_65_V06', 'MAT-OLED-R', '红光有机材料', 'EVAPORATION', 0.080000, 'g', 1, 'OLED-R'),
    ('BOM_65_V06', 'MAT-ENCAP-001', '封装胶', 'ENCAPSULATION', 0.160000, 'g', 1, 'ENCAP'),
    ('BOM_67_V04', 'MAT-PI-001', 'PI胶', 'COATING', 0.450000, 'g', 1, 'PI'),
    ('BOM_67_V04', 'MAT-OLED-R', '红光有机材料', 'EVAPORATION', 0.090000, 'g', 1, 'OLED-R'),
    ('BOM_67_V04', 'MAT-ENCAP-001', '封装胶', 'ENCAPSULATION', 0.180000, 'g', 1, 'ENCAP')
) AS item(bom_code, material_code, material_name, step_code, required_qty, unit, is_key_material, substitute_group)
ON bom.bom_code = item.bom_code
ON CONFLICT (bom_id, material_code, step_code) DO NOTHING;

INSERT INTO material_batch (
    material_code, material_name, batch_no, supplier_code, total_qty, available_qty,
    reserved_qty, consumed_qty, unit, status, quality_status, received_time,
    expire_time, location, fifo_seq, created_by
)
VALUES
('MAT-PI-001', 'PI胶', 'PI260606-A', 'SUP-PI-01', 1000.000000, 820.000000, 120.000000, 60.000000, 'g', 'AVAILABLE', 'PASS', CURRENT_TIMESTAMP - INTERVAL '20 hours', CURRENT_TIMESTAMP + INTERVAL '15 days', 'WMS-A-01', 10, 'system'),
('MAT-PI-001', 'PI胶', 'PI260605-Z', 'SUP-PI-01', 600.000000, 96.000000, 0.000000, 504.000000, 'g', 'AVAILABLE', 'PASS', CURRENT_TIMESTAMP - INTERVAL '44 hours', CURRENT_TIMESTAMP + INTERVAL '14 days', 'WMS-A-02', 5, 'system'),
('MAT-OLED-R', '红光有机材料', 'OLED-R-260605-B', 'SUP-OLED-02', 500.000000, 310.000000, 20.000000, 170.000000, 'g', 'AVAILABLE', 'PASS', CURRENT_TIMESTAMP - INTERVAL '30 hours', CURRENT_TIMESTAMP + INTERVAL '18 days', 'COLD-01', 20, 'system'),
('MAT-ENCAP-001', '封装胶', 'ENCAP260604-C', 'SUP-ENCAP-01', 700.000000, 540.000000, 30.000000, 130.000000, 'g', 'AVAILABLE', 'PASS', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP + INTERVAL '30 days', 'WMS-B-03', 30, 'system')
ON CONFLICT (batch_no) DO NOTHING;

INSERT INTO material_carrier (carrier_no, carrier_type, status, lot_no, product_code, step_code, equipment_code, bind_time, location)
VALUES
('CST-260606-001', 'Cassette', 'BOUND', 'LOT202406001', 'AMOLED_65', 'COATING', 'COATER_01', CURRENT_TIMESTAMP - INTERVAL '30 minutes', 'LINE_01'),
('CST-260606-002', 'Cassette', 'IDLE', NULL, NULL, NULL, NULL, NULL, 'STOCKER_01'),
('TRAY-260606-009', 'Tray', 'CLEANING', NULL, NULL, NULL, NULL, NULL, 'CLEAN_ROOM')
ON CONFLICT (carrier_no) DO NOTHING;

INSERT INTO material_consumption (
    consumption_no, lot_no, order_no, product_code, step_code, equipment_code,
    material_code, material_name, batch_no, consumed_qty, unit, operator, consume_time,
    step_record_id, trace_status
)
VALUES
('MC-SEED-LOT202406001-001', 'LOT202406001', 'MO202406001', 'AMOLED_65', 'COATING', 'COATER_01', 'MAT-PI-001', 'PI胶', 'PI260606-A', 42.800000, 'g', 'op1007', CURRENT_TIMESTAMP - INTERVAL '2 hours', NULL, 'TRACEABLE'),
('MC-SEED-LOT202406004-001', 'LOT202406004', 'MO202406001', 'AMOLED_65', 'EVAPORATION', 'EVAP_01', 'MAT-OLED-R', '红光有机材料', 'OLED-R-260605-B', 8.200000, 'g', 'op1011', CURRENT_TIMESTAMP - INTERVAL '95 minutes', NULL, 'TRACEABLE')
ON CONFLICT (consumption_no) DO NOTHING;
