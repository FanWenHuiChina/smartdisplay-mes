CREATE TABLE IF NOT EXISTS md_material_location (
    id BIGSERIAL PRIMARY KEY,
    location_code VARCHAR(60) NOT NULL UNIQUE,
    zone_code VARCHAR(60),
    area_code VARCHAR(60),
    storage_type VARCHAR(40) NOT NULL DEFAULT 'NORMAL',
    material_class VARCHAR(40) NOT NULL DEFAULT 'ANY',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    capacity_qty NUMERIC(18, 6),
    used_qty NUMERIC(18, 6) DEFAULT 0,
    unit VARCHAR(20),
    temperature_min NUMERIC(10, 2),
    temperature_max NUMERIC(10, 2),
    humidity_min NUMERIC(10, 2),
    humidity_max NUMERIC(10, 2),
    strategy_priority INT DEFAULT 100,
    remark VARCHAR(500),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_location_strategy
ON md_material_location(status, material_class, unit, strategy_priority);

INSERT INTO md_material_location (
    location_code, zone_code, area_code, storage_type, material_class, status,
    capacity_qty, used_qty, unit, temperature_min, temperature_max,
    humidity_min, humidity_max, strategy_priority, remark
)
VALUES
('WMS-IN', 'WMS-INBOUND', 'WMS', 'NORMAL', 'ANY', 'ACTIVE', 10000.000000, 0.000000, 'EA', 18.00, 28.00, 30.00, 70.00, 10, '默认入库暂存位'),
('WMS-A01', 'CHEM-A', 'WMS', 'CHEMICAL', 'CHEMICAL', 'ACTIVE', 5000.000000, 940.000000, 'g', 18.00, 25.00, 30.00, 55.00, 20, 'PI胶和封装胶化学品库位'),
('COLD-02', 'ORG-COLD', 'WMS', 'COLD', 'ORGANIC', 'ACTIVE', 2000.000000, 322.000000, 'g', 2.00, 8.00, 20.00, 45.00, 30, 'OLED有机材料低温库位'),
('WMS-B03', 'GENERAL-B', 'WMS', 'NORMAL', 'GENERAL', 'ACTIVE', 6000.000000, 546.000000, 'g', 18.00, 28.00, 30.00, 70.00, 40, '通用辅料库位'),
('WMS-HOLD-01', 'HOLD', 'WMS', 'QUARANTINE', 'ANY', 'LOCKED', 1000.000000, 0.000000, 'EA', 18.00, 28.00, 30.00, 70.00, 900, '待处置/隔离库位')
ON CONFLICT (location_code) DO UPDATE
SET zone_code = EXCLUDED.zone_code,
    area_code = EXCLUDED.area_code,
    storage_type = EXCLUDED.storage_type,
    material_class = EXCLUDED.material_class,
    status = EXCLUDED.status,
    capacity_qty = EXCLUDED.capacity_qty,
    unit = EXCLUDED.unit,
    temperature_min = EXCLUDED.temperature_min,
    temperature_max = EXCLUDED.temperature_max,
    humidity_min = EXCLUDED.humidity_min,
    humidity_max = EXCLUDED.humidity_max,
    strategy_priority = EXCLUDED.strategy_priority,
    remark = EXCLUDED.remark,
    updated_time = CURRENT_TIMESTAMP;
