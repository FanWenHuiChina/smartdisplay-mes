CREATE TABLE IF NOT EXISTS md_recipe (
    id BIGSERIAL PRIMARY KEY,
    recipe_code VARCHAR(50) NOT NULL UNIQUE,
    recipe_name VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50) NOT NULL,
    recipe_version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    description TEXT,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_recipe_product_step_equip_ver
ON md_recipe(product_code, step_code, equipment_code, recipe_version) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_recipe_product ON md_recipe(product_code);
CREATE INDEX IF NOT EXISTS idx_recipe_step ON md_recipe(step_code);
CREATE INDEX IF NOT EXISTS idx_recipe_equipment ON md_recipe(equipment_code);
CREATE INDEX IF NOT EXISTS idx_recipe_status ON md_recipe(status);

CREATE TABLE IF NOT EXISTS md_recipe_param (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    param_name VARCHAR(100) NOT NULL,
    param_code VARCHAR(50),
    target_value DECIMAL(18,4),
    upper_limit DECIMAL(18,4),
    lower_limit DECIMAL(18,4),
    unit VARCHAR(20),
    param_type VARCHAR(20),
    is_key_param SMALLINT DEFAULT 0,
    display_order INT,
    remark VARCHAR(200),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recipe_param_recipe FOREIGN KEY (recipe_id) REFERENCES md_recipe(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_recipe_param_recipe_id ON md_recipe_param(recipe_id);
CREATE INDEX IF NOT EXISTS idx_recipe_param_name ON md_recipe_param(param_name);

CREATE TABLE IF NOT EXISTS md_process_step (
    id BIGSERIAL PRIMARY KEY,
    step_code VARCHAR(50) NOT NULL UNIQUE,
    step_name VARCHAR(100) NOT NULL,
    step_type VARCHAR(50),
    segment VARCHAR(20),
    need_recipe SMALLINT DEFAULT 1,
    need_qc SMALLINT DEFAULT 0,
    allow_rework SMALLINT DEFAULT 1,
    description TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS md_equipment (
    id BIGSERIAL PRIMARY KEY,
    equipment_code VARCHAR(50) NOT NULL UNIQUE,
    equipment_name VARCHAR(100) NOT NULL,
    equipment_type VARCHAR(50),
    line_code VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    capability_steps TEXT,
    location VARCHAR(100),
    description TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS prod_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    product_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(100),
    planned_qty INT NOT NULL,
    completed_qty INT DEFAULT 0,
    priority INT DEFAULT 0,
    line_code VARCHAR(50),
    route_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_order_no ON prod_order(order_no);
CREATE INDEX IF NOT EXISTS idx_order_product ON prod_order(product_code);
CREATE INDEX IF NOT EXISTS idx_order_status ON prod_order(status);

CREATE TABLE IF NOT EXISTS prod_lot (
    id BIGSERIAL PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL UNIQUE,
    order_no VARCHAR(50) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    qty INT NOT NULL,
    current_step_code VARCHAR(50),
    current_equipment_code VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    hold_flag SMALLINT DEFAULT 0,
    priority INT DEFAULT 0,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lot_no ON prod_lot(lot_no);
CREATE INDEX IF NOT EXISTS idx_lot_order ON prod_lot(order_no);
CREATE INDEX IF NOT EXISTS idx_lot_product ON prod_lot(product_code);
CREATE INDEX IF NOT EXISTS idx_lot_status ON prod_lot(status);
CREATE INDEX IF NOT EXISTS idx_lot_current_step ON prod_lot(current_step_code);

CREATE TABLE IF NOT EXISTS prod_lot_step_record (
    id BIGSERIAL PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL,
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50) NOT NULL,
    recipe_code VARCHAR(50),
    track_in_time TIMESTAMP,
    track_out_time TIMESTAMP,
    operator VARCHAR(50),
    process_params TEXT,
    result VARCHAR(20),
    remark VARCHAR(500),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_step_record_lot ON prod_lot_step_record(lot_no);
CREATE INDEX IF NOT EXISTS idx_step_record_step ON prod_lot_step_record(step_code);
CREATE INDEX IF NOT EXISTS idx_step_record_equipment ON prod_lot_step_record(equipment_code);
CREATE INDEX IF NOT EXISTS idx_step_record_track_in ON prod_lot_step_record(track_in_time);

CREATE TABLE IF NOT EXISTS lot_hold_record (
    id BIGSERIAL PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL,
    hold_reason VARCHAR(200) NOT NULL,
    hold_type VARCHAR(50),
    hold_by VARCHAR(50),
    hold_time TIMESTAMP NOT NULL,
    release_by VARCHAR(50),
    release_time TIMESTAMP,
    disposition VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'HOLD',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_hold_lot ON lot_hold_record(lot_no);
CREATE INDEX IF NOT EXISTS idx_hold_status ON lot_hold_record(status);
