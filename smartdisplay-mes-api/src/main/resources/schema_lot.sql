-- ====================================
-- Lot管理与Track In/Out相关表
-- ====================================

-- 工序定义表
CREATE TABLE md_process_step (
    id BIGSERIAL PRIMARY KEY,
    step_code VARCHAR(50) NOT NULL UNIQUE COMMENT '工序编码',
    step_name VARCHAR(100) NOT NULL COMMENT '工序名称',
    step_type VARCHAR(50) COMMENT '工序类型',
    segment VARCHAR(20) COMMENT '生产段: Array-背板, Cell-蒸镀封装, Module-模组',
    need_recipe SMALLINT DEFAULT 1 COMMENT '是否需要Recipe: 0-否, 1-是',
    need_qc SMALLINT DEFAULT 0 COMMENT '是否需要质检: 0-否, 1-是',
    allow_rework SMALLINT DEFAULT 1 COMMENT '是否允许返工: 0-否, 1-是',
    description TEXT COMMENT '描述',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE md_process_step IS '工序定义表';

-- 插入测试工序数据
INSERT INTO md_process_step (step_code, step_name, segment, need_recipe, need_qc) VALUES
('CLEAN', '清洗', 'Array', 1, 0),
('COATING', 'PI涂胶', 'Array', 1, 0),
('EXPOSURE', '曝光', 'Array', 1, 0),
('DEVELOP', '显影', 'Array', 1, 0),
('ETCH', '蚀刻', 'Array', 1, 0),
('STRIP', '剥膜', 'Array', 1, 0),
('EVAPORATION', '蒸镀', 'Cell', 1, 1),
('ENCAPSULATION', '封装', 'Cell', 1, 1),
('INSPECTION', 'AOI检测', 'Cell', 0, 1),
('AGING', '老化', 'Module', 1, 0);

-- 设备表
CREATE TABLE md_equipment (
    id BIGSERIAL PRIMARY KEY,
    equipment_code VARCHAR(50) NOT NULL UNIQUE COMMENT '设备编码',
    equipment_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    equipment_type VARCHAR(50) COMMENT '设备类型',
    line_code VARCHAR(50) COMMENT '产线编码',
    status VARCHAR(20) NOT NULL DEFAULT 'IDLE' COMMENT '状态: IDLE-空闲, RUNNING-运行中, ALARM-报警, DOWN-宕机, PM-保养, OFFLINE-离线',
    capability_steps TEXT COMMENT '支持的工序列表(JSON数组)',
    location VARCHAR(100) COMMENT '位置',
    description TEXT COMMENT '描述',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE md_equipment IS '设备表';

-- 插入测试设备数据
INSERT INTO md_equipment (equipment_code, equipment_name, equipment_type, line_code, status, capability_steps) VALUES
('COATER_01', '涂胶机-1', 'Coater', 'LINE_01', 'IDLE', '["COATING"]'),
('COATER_02', '涂胶机-2', 'Coater', 'LINE_01', 'IDLE', '["COATING"]'),
('EVAP_01', '蒸镀机-1', 'Evaporator', 'LINE_01', 'IDLE', '["EVAPORATION"]'),
('INSPECT_01', 'AOI检测机-1', 'AOI', 'LINE_01', 'IDLE', '["INSPECTION"]'),
('ETCH_01', '蚀刻机-1', 'Etcher', 'LINE_01', 'IDLE', '["ETCH"]');

-- 生产工单表
CREATE TABLE prod_order (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE COMMENT '工单号',
    product_code VARCHAR(50) NOT NULL COMMENT '产品型号',
    product_name VARCHAR(100) COMMENT '产品名称',
    planned_qty INT NOT NULL COMMENT '计划数量',
    completed_qty INT DEFAULT 0 COMMENT '完成数量',
    priority INT DEFAULT 0 COMMENT '优先级: 数字越大优先级越高',
    line_code VARCHAR(50) COMMENT '产线编码',
    route_id BIGINT COMMENT '工艺路线ID',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED-已创建, RELEASED-已释放, IN_PROGRESS-进行中, HOLD-暂停, COMPLETED-完成, CLOSED-关闭',
    start_time TIMESTAMP COMMENT '开始时间',
    end_time TIMESTAMP COMMENT '结束时间',
    created_by VARCHAR(50) COMMENT '创建人',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE prod_order IS '生产工单表';

-- 索引
CREATE INDEX idx_order_no ON prod_order(order_no);
CREATE INDEX idx_order_product ON prod_order(product_code);
CREATE INDEX idx_order_status ON prod_order(status);

-- Lot批次表
CREATE TABLE prod_lot (
    id BIGSERIAL PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL UNIQUE COMMENT 'Lot批次号',
    order_no VARCHAR(50) NOT NULL COMMENT '工单号',
    product_code VARCHAR(50) NOT NULL COMMENT '产品型号',
    qty INT NOT NULL COMMENT '数量',
    current_step_code VARCHAR(50) COMMENT '当前工序编码',
    current_equipment_code VARCHAR(50) COMMENT '当前设备编码',
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED' COMMENT '状态: CREATED-已创建, READY-就绪, PROCESSING-加工中, HOLD-暂停, COMPLETED-完成, SCRAPPED-报废',
    hold_flag SMALLINT DEFAULT 0 COMMENT 'Hold标记: 0-正常, 1-已Hold',
    priority INT DEFAULT 0 COMMENT '优先级',
    created_by VARCHAR(50) COMMENT '创建人',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE prod_lot IS 'Lot批次表';

-- 索引
CREATE UNIQUE INDEX uk_lot_no ON prod_lot(lot_no);
CREATE INDEX idx_lot_order ON prod_lot(order_no);
CREATE INDEX idx_lot_product ON prod_lot(product_code);
CREATE INDEX idx_lot_status ON prod_lot(status);
CREATE INDEX idx_lot_current_step ON prod_lot(current_step_code);

-- Lot过站记录表
CREATE TABLE prod_lot_step_record (
    id BIGSERIAL PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL COMMENT 'Lot批次号',
    step_code VARCHAR(50) NOT NULL COMMENT '工序编码',
    equipment_code VARCHAR(50) NOT NULL COMMENT '设备编码',
    recipe_code VARCHAR(50) COMMENT 'Recipe编码',
    track_in_time TIMESTAMP COMMENT '进站时间',
    track_out_time TIMESTAMP COMMENT '出站时间',
    operator VARCHAR(50) COMMENT '操作员',
    process_params TEXT COMMENT '加工参数(JSON)',
    result VARCHAR(20) COMMENT '结果: OK-合格, NG-不合格',
    remark VARCHAR(500) COMMENT '备注',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE prod_lot_step_record IS 'Lot过站记录表';

-- 索引
CREATE INDEX idx_step_record_lot ON prod_lot_step_record(lot_no);
CREATE INDEX idx_step_record_step ON prod_lot_step_record(step_code);
CREATE INDEX idx_step_record_equipment ON prod_lot_step_record(equipment_code);
CREATE INDEX idx_step_record_track_in ON prod_lot_step_record(track_in_time);

-- Hold记录表
CREATE TABLE lot_hold_record (
    id BIGSERIAL PRIMARY KEY,
    lot_no VARCHAR(50) NOT NULL COMMENT 'Lot批次号',
    hold_reason VARCHAR(200) NOT NULL COMMENT 'Hold原因',
    hold_type VARCHAR(50) COMMENT 'Hold类型: QUALITY-质量异常, EQUIPMENT-设备故障, MATERIAL-物料问题, ENGINEERING-工程变更',
    hold_by VARCHAR(50) COMMENT 'Hold操作人',
    hold_time TIMESTAMP NOT NULL COMMENT 'Hold时间',
    release_by VARCHAR(50) COMMENT 'Release操作人',
    release_time TIMESTAMP COMMENT 'Release时间',
    disposition VARCHAR(200) COMMENT '处置结果',
    status VARCHAR(20) NOT NULL DEFAULT 'HOLD' COMMENT '状态: HOLD-已Hold, RELEASED-已Release',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE lot_hold_record IS 'Hold记录表';

-- 索引
CREATE INDEX idx_hold_lot ON lot_hold_record(lot_no);
CREATE INDEX idx_hold_status ON lot_hold_record(status);

-- 插入测试工单数据
INSERT INTO prod_order (order_no, product_code, product_name, planned_qty, line_code, status, created_by)
VALUES
('MO202406001', 'AMOLED_65', 'AMOLED 6.5寸柔性屏', 1000, 'LINE_01', 'RELEASED', 'system');

-- 插入测试Lot数据
INSERT INTO prod_lot (lot_no, order_no, product_code, qty, status, created_by)
VALUES
('LOT202406001', 'MO202406001', 'AMOLED_65', 100, 'READY', 'system'),
('LOT202406002', 'MO202406001', 'AMOLED_65', 100, 'READY', 'system');
