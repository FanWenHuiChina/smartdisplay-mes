ALTER TABLE material_batch
    ADD COLUMN IF NOT EXISTS frozen_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS returned_qty DECIMAL(18,6) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_count_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS stock_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS material_inventory_txn (
    id BIGSERIAL PRIMARY KEY,
    txn_no VARCHAR(80) NOT NULL UNIQUE,
    txn_type VARCHAR(30) NOT NULL,
    material_code VARCHAR(50) NOT NULL,
    material_name VARCHAR(100),
    batch_no VARCHAR(60) NOT NULL,
    supplier_code VARCHAR(50),
    qty_delta DECIMAL(18,6) NOT NULL DEFAULT 0,
    available_before DECIMAL(18,6) NOT NULL DEFAULT 0,
    available_after DECIMAL(18,6) NOT NULL DEFAULT 0,
    frozen_before DECIMAL(18,6) NOT NULL DEFAULT 0,
    frozen_after DECIMAL(18,6) NOT NULL DEFAULT 0,
    reserved_before DECIMAL(18,6) NOT NULL DEFAULT 0,
    reserved_after DECIMAL(18,6) NOT NULL DEFAULT 0,
    counted_qty DECIMAL(18,6),
    unit VARCHAR(20),
    reason VARCHAR(300),
    source_system VARCHAR(80) NOT NULL DEFAULT 'wms-adapter',
    operator VARCHAR(80),
    txn_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_material_inventory_txn_batch ON material_inventory_txn(batch_no);
CREATE INDEX IF NOT EXISTS idx_material_inventory_txn_type_time ON material_inventory_txn(txn_type, txn_time);
CREATE INDEX IF NOT EXISTS idx_material_inventory_txn_material ON material_inventory_txn(material_code);
