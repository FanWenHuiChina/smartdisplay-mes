-- SmartDisplay MES 数据库初始化脚本 (PostgreSQL版本)

-- Recipe主表
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

-- Recipe参数表
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

-- 工序定义表
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

-- 工艺路线主表
CREATE TABLE IF NOT EXISTS md_route (
    id BIGSERIAL PRIMARY KEY,
    route_code VARCHAR(50) NOT NULL UNIQUE,
    route_name VARCHAR(100) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    route_version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    effective_time TIMESTAMP,
    description TEXT,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_route_product_version
ON md_route(product_code, route_version) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_route_product_status ON md_route(product_code, status);

-- 工艺路线工序明细
CREATE TABLE IF NOT EXISTS md_route_step (
    id BIGSERIAL PRIMARY KEY,
    route_id BIGINT NOT NULL,
    step_code VARCHAR(50) NOT NULL,
    step_seq INT NOT NULL,
    segment VARCHAR(20),
    allow_rework SMALLINT DEFAULT 1,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_route_step_route FOREIGN KEY (route_id) REFERENCES md_route(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_route_step_seq ON md_route_step(route_id, step_seq);
CREATE INDEX IF NOT EXISTS idx_route_step_code ON md_route_step(step_code);

-- 设备表
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

-- 生产工单表
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

-- Lot批次表
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

-- Lot过站记录表
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

-- Hold记录表
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

-- 质量检验记录表
CREATE TABLE IF NOT EXISTS quality_inspection (
    id BIGSERIAL PRIMARY KEY,
    inspection_no VARCHAR(60) NOT NULL UNIQUE,
    lot_no VARCHAR(50) NOT NULL,
    order_no VARCHAR(50),
    product_code VARCHAR(50),
    step_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50),
    recipe_code VARCHAR(50),
    item_code VARCHAR(80) NOT NULL,
    item_name VARCHAR(120),
    measured_value DECIMAL(18,6),
    upper_limit DECIMAL(18,6),
    lower_limit DECIMAL(18,6),
    unit VARCHAR(20),
    result VARCHAR(20) NOT NULL,
    defect_code VARCHAR(50),
    defect_position VARCHAR(100),
    inspector VARCHAR(50),
    inspection_time TIMESTAMP NOT NULL,
    source VARCHAR(50),
    remark VARCHAR(500),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_inspection_lot ON quality_inspection(lot_no);
CREATE INDEX IF NOT EXISTS idx_quality_inspection_result ON quality_inspection(result);
CREATE INDEX IF NOT EXISTS idx_quality_inspection_time ON quality_inspection(inspection_time);

-- 缺陷记录表
CREATE TABLE IF NOT EXISTS quality_defect_record (
    id BIGSERIAL PRIMARY KEY,
    defect_no VARCHAR(60) NOT NULL UNIQUE,
    inspection_no VARCHAR(60),
    lot_no VARCHAR(50) NOT NULL,
    step_code VARCHAR(50),
    equipment_code VARCHAR(50),
    defect_code VARCHAR(50) NOT NULL,
    defect_name VARCHAR(100),
    defect_level VARCHAR(20),
    defect_position VARCHAR(100),
    qty INT DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    disposition VARCHAR(80),
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_defect_lot ON quality_defect_record(lot_no);
CREATE INDEX IF NOT EXISTS idx_quality_defect_code ON quality_defect_record(defect_code);
CREATE INDEX IF NOT EXISTS idx_quality_defect_status ON quality_defect_record(status);

-- 异常事件表
CREATE TABLE IF NOT EXISTS exception_event (
    id BIGSERIAL PRIMARY KEY,
    event_no VARCHAR(60) NOT NULL UNIQUE,
    event_type VARCHAR(50) NOT NULL,
    event_level VARCHAR(20) NOT NULL,
    lot_no VARCHAR(50),
    order_no VARCHAR(50),
    step_code VARCHAR(50),
    equipment_code VARCHAR(50),
    source_module VARCHAR(50),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    owner_role VARCHAR(50),
    owner_user VARCHAR(50),
    occurred_time TIMESTAMP NOT NULL,
    closed_time TIMESTAMP,
    close_conclusion VARCHAR(500),
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exception_event_lot ON exception_event(lot_no);
CREATE INDEX IF NOT EXISTS idx_exception_event_status ON exception_event(status);
CREATE INDEX IF NOT EXISTS idx_exception_event_time ON exception_event(occurred_time);

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100),
    role VARCHAR(20) DEFAULT 'OPERATOR',
    status SMALLINT DEFAULT 1,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);

-- 系统审计日志表
CREATE TABLE IF NOT EXISTS sys_audit_log (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(80) NOT NULL,
    biz_no VARCHAR(100),
    biz_type VARCHAR(50),
    description VARCHAR(500),
    operator VARCHAR(80),
    result VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    source VARCHAR(100),
    request_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_biz_no ON sys_audit_log(biz_no);
CREATE INDEX IF NOT EXISTS idx_audit_action ON sys_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_created_time ON sys_audit_log(created_time);

-- AI分析结果留痕表
CREATE TABLE IF NOT EXISTS ai_report_record (
    id BIGSERIAL PRIMARY KEY,
    report_no VARCHAR(80) NOT NULL UNIQUE,
    report_type VARCHAR(50) NOT NULL,
    biz_no VARCHAR(120),
    biz_type VARCHAR(50),
    prompt_template_version VARCHAR(50) NOT NULL,
    model_name VARCHAR(80) NOT NULL,
    input_snapshot TEXT NOT NULL,
    output_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_report_type ON ai_report_record(report_type);
CREATE INDEX IF NOT EXISTS idx_ai_report_biz ON ai_report_record(biz_type, biz_no);
CREATE INDEX IF NOT EXISTS idx_ai_report_created_time ON ai_report_record(created_time);

-- AI知识库文档与切片表
CREATE TABLE IF NOT EXISTS ai_kb_document (
    id BIGSERIAL PRIMARY KEY,
    document_no VARCHAR(80) NOT NULL UNIQUE,
    document_name VARCHAR(160) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    doc_version VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_uri VARCHAR(300),
    owner_role VARCHAR(50),
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_no VARCHAR(100) NOT NULL UNIQUE,
    chunk_title VARCHAR(160) NOT NULL,
    chunk_seq INT NOT NULL,
    content TEXT NOT NULL,
    keywords VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_kb_chunk_document FOREIGN KEY (document_id) REFERENCES ai_kb_document(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_kb_document_type ON ai_kb_document(document_type, status);
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_document ON ai_kb_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_status ON ai_kb_chunk(status);

-- 插入测试数据
-- 试点角色账号（密码均为：123456）
INSERT INTO sys_user (username, password, real_name, role, status)
VALUES
('admin', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '系统管理员', 'ADMIN', 1),
('planner', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '生产计划员', 'PLANNER', 1),
('operator', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '产线操作员', 'OPERATOR', 1),
('qe', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '质量工程师', 'QE', 1),
('pe', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '工艺工程师', 'PE', 1),
('ee', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '设备工程师', 'EE', 1),
('engineer', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '工艺工程师', 'PE', 1)
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    real_name = EXCLUDED.real_name,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    updated_time = CURRENT_TIMESTAMP;

-- AI知识库种子数据
INSERT INTO ai_kb_document (document_no, document_name, document_type, doc_version, status, source_uri, owner_role, created_by)
VALUES
('SOP-HOLD-001', 'AMOLED异常Hold与Release处置SOP', 'SOP', 'V1.0', 'ACTIVE', 'docs/sop/SOP-HOLD-001.md', 'QE', 'system'),
('MANUAL-EVAP-001', '蒸镀设备报警排查手册', 'EQUIPMENT_MANUAL', 'V1.0', 'ACTIVE', 'docs/manual/MANUAL-EVAP-001.md', 'EE', 'system'),
('QMS-MURA-001', 'Mura缺陷判定标准', 'QUALITY_STANDARD', 'V1.0', 'ACTIVE', 'docs/qms/QMS-MURA-001.md', 'QE', 'system'),
('SOP-MATERIAL-001', '关键物料批次追溯SOP', 'SOP', 'V1.0', 'ACTIVE', 'docs/sop/SOP-MATERIAL-001.md', 'PLANNER', 'system')
ON CONFLICT (document_no) DO UPDATE SET
    document_name = EXCLUDED.document_name,
    document_type = EXCLUDED.document_type,
    doc_version = EXCLUDED.doc_version,
    status = EXCLUDED.status,
    source_uri = EXCLUDED.source_uri,
    owner_role = EXCLUDED.owner_role,
    updated_time = CURRENT_TIMESTAMP;

INSERT INTO ai_kb_chunk (document_id, chunk_no, chunk_title, chunk_seq, content, keywords, status)
SELECT doc.id, item.chunk_no, item.chunk_title, item.chunk_seq, item.content, item.keywords, 'ACTIVE'
FROM ai_kb_document doc
JOIN (
    VALUES
    ('SOP-HOLD-001', 'SOP-HOLD-001-001', '异常隔离与Hold原则', 1, '发现质量NG、Recipe关键参数超限、设备报警影响当前Lot时，应先Hold Lot并隔离影响范围；记录原因、责任模块、处置建议和审批人，禁止自动放行。', 'hold release recipe 参数超限 设备报警 质量NG 隔离 审批'),
    ('SOP-HOLD-001', 'SOP-HOLD-001-002', 'Release放行条件', 2, 'Release必须有复判结论、处置说明和审批人；质量类Hold由QE确认，工艺类Hold由PE确认，设备类Hold由EE确认，放行后Lot回到可执行状态。', 'release 放行 复判 QE PE EE 审批 可执行'),
    ('SOP-HOLD-001', 'SOP-HOLD-001-003', 'Rework与Scrap边界', 3, 'Rework必须选择返工路线和返工起始工序；Scrap必须二次确认并记录报废原因、责任模块、审批人和时间。', 'rework scrap 返工 报废 路线 起始工序 二次确认'),
    ('MANUAL-EVAP-001', 'MANUAL-EVAP-001-001', '蒸镀真空波动排查', 1, 'EVAP设备发生真空波动时，先确认最近2小时报警趋势、真空泵状态、腔体压力曲线和PM后稳定时间；受影响Lot需结合近期Mura缺陷趋势评估风险。', 'EVAP 蒸镀 真空 波动 报警 真空泵 Mura PM'),
    ('MANUAL-EVAP-001', 'MANUAL-EVAP-001-002', '设备异常与Lot风险', 2, '设备报警未关闭前不建议新Lot进站；已加工Lot应通过AOI抽检、参数快照和设备事件时间窗判断是否需要Hold或MRB复判。', '设备异常 Lot 进站 AOI 参数快照 MRB Hold'),
    ('QMS-MURA-001', 'QMS-MURA-001-001', 'Mura缺陷判定', 1, 'Mura缺陷需结合AOI结果、缺陷位置、批次集中度和相关设备事件综合判定；连续Lot出现同类Mura时应升级为P1质量异常。', 'Mura 缺陷 AOI 位置 批次 设备事件 P1 质量异常'),
    ('SOP-MATERIAL-001', 'SOP-MATERIAL-001-001', '关键物料批次齐套', 1, 'Track In前必须校验关键物料批次齐套、质量状态PASS、可用量满足BOM需求；批次锁定后Track Out生成消耗追溯记录。', '物料 批次 齐套 Track In Track Out BOM PASS 消耗追溯')
) AS item(document_no, chunk_no, chunk_title, chunk_seq, content, keywords)
ON doc.document_no = item.document_no
ON CONFLICT (chunk_no) DO UPDATE SET
    chunk_title = EXCLUDED.chunk_title,
    chunk_seq = EXCLUDED.chunk_seq,
    content = EXCLUDED.content,
    keywords = EXCLUDED.keywords,
    status = EXCLUDED.status;

-- Recipe数据（5条，覆盖不同工序）
INSERT INTO md_recipe (recipe_code, recipe_name, product_code, step_code, equipment_code, recipe_version, status, description, created_by)
VALUES
('RCP_COAT_001', 'AMOLED 6.5寸涂胶Recipe', 'AMOLED_65', 'COATING', 'COATER_01', 'V1.0', 'ACTIVE', '用于AMOLED 6.5寸产品的PI涂胶工序标准参数', 'system'),
('RCP_COAT_002', 'AMOLED 6.7寸涂胶Recipe', 'AMOLED_67', 'COATING', 'COATER_02', 'V1.0', 'ACTIVE', '用于AMOLED 6.7寸柔性屏PI涂胶工序', 'system'),
('RCP_EVAP_001', 'AMOLED蒸镀Recipe', 'AMOLED_65', 'EVAPORATION', 'EVAP_01', 'V2.1', 'ACTIVE', 'RGB有机材料蒸镀参数', 'system'),
('RCP_ETCH_001', '蚀刻标准Recipe', 'AMOLED_65', 'ETCH', 'ETCH_01', 'V1.5', 'ACTIVE', 'Array蚀刻工序参数', 'system'),
('RCP_COAT_OLD', '旧版涂胶Recipe', 'AMOLED_65', 'COATING', 'COATER_01', 'V0.9', 'INACTIVE', '已废弃的旧版本', 'system')
ON CONFLICT (recipe_code) DO NOTHING;

-- Recipe参数（涂胶Recipe）
INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶温度', 'TEMP_COATING', 150.0, 155.0, 145.0, '℃', 'TEMPERATURE', 1, 1
FROM md_recipe WHERE recipe_code = 'RCP_COAT_001' LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶速度', 'SPEED_COATING', 300.0, 320.0, 280.0, 'mm/s', 'SPEED', 1, 2
FROM md_recipe WHERE recipe_code = 'RCP_COAT_001' LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶厚度', 'THICKNESS', 2.0, 2.2, 1.8, 'μm', 'DIMENSION', 1, 3
FROM md_recipe WHERE recipe_code = 'RCP_COAT_001' LIMIT 1;

-- Recipe参数（COATER_02涂胶Recipe，用于参数超限自动Hold演示）
INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶温度', 'TEMP_COATING', 150.0, 155.0, 145.0, '℃', 'TEMPERATURE', 1, 1
FROM md_recipe WHERE recipe_code = 'RCP_COAT_002' LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶速度', 'SPEED_COATING', 300.0, 320.0, 280.0, 'mm/s', 'SPEED', 1, 2
FROM md_recipe WHERE recipe_code = 'RCP_COAT_002' LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '涂胶厚度', 'THICKNESS', 2.0, 2.2, 1.8, 'μm', 'DIMENSION', 1, 3
FROM md_recipe WHERE recipe_code = 'RCP_COAT_002' LIMIT 1;

-- Recipe参数（蒸镀Recipe）
INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '蒸镀温度', 'TEMP_EVAP', 280.0, 285.0, 275.0, '℃', 'TEMPERATURE', 1, 1
FROM md_recipe WHERE recipe_code = 'RCP_EVAP_001' LIMIT 1;

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT id, '真空度', 'VACUUM', 1.0e-4, 5.0e-4, 1.0e-5, 'Pa', 'PRESSURE', 1, 2
FROM md_recipe WHERE recipe_code = 'RCP_EVAP_001' LIMIT 1;

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
('AGING', '老化', 'Module', 1, 0)
ON CONFLICT (step_code) DO NOTHING;

INSERT INTO md_route (route_code, route_name, product_code, route_version, status, effective_time, description, created_by)
VALUES
('RTE_G6_AMOLED65_V08', 'AMOLED 6.5寸柔性屏试点路线', 'AMOLED_65', 'V08', 'ACTIVE', CURRENT_TIMESTAMP, '单产线试点路线', 'system'),
('RTE_G6_AMOLED67_V05', 'AMOLED 6.7寸柔性屏试点路线', 'AMOLED_67', 'V05', 'ACTIVE', CURRENT_TIMESTAMP, '单产线试点路线', 'system')
ON CONFLICT (route_code) DO NOTHING;

INSERT INTO md_route_step (route_id, step_code, step_seq, segment, allow_rework)
SELECT route.id, step.step_code, step.step_seq, step.segment, step.allow_rework
FROM md_route route
JOIN (
    VALUES
    ('CLEAN', 10, 'Array', 1),
    ('COATING', 20, 'Array', 1),
    ('EXPOSURE', 30, 'Array', 1),
    ('ETCH', 40, 'Array', 1),
    ('EVAPORATION', 50, 'Cell', 1),
    ('ENCAPSULATION', 60, 'Cell', 1),
    ('INSPECTION', 70, 'Cell', 1),
    ('AGING', 80, 'Module', 0)
) AS step(step_code, step_seq, segment, allow_rework)
ON route.route_code IN ('RTE_G6_AMOLED65_V08', 'RTE_G6_AMOLED67_V05')
ON CONFLICT (route_id, step_seq) DO NOTHING;

INSERT INTO md_equipment (equipment_code, equipment_name, equipment_type, line_code, status, capability_steps) VALUES
('COATER_01', '涂胶机-1', 'Coater', 'LINE_01', 'IDLE', '["COATING"]'),
('COATER_02', '涂胶机-2', 'Coater', 'LINE_01', 'RUNNING', '["COATING"]'),
('EVAP_01', '蒸镀机-1', 'Evaporator', 'LINE_01', 'RUNNING', '["EVAPORATION"]'),
('INSPECT_01', 'AOI检测机-1', 'AOI', 'LINE_01', 'IDLE', '["INSPECTION"]'),
('ETCH_01', '蚀刻机-1', 'Etcher', 'LINE_01', 'ALARM', '["ETCH"]')
ON CONFLICT (equipment_code) DO NOTHING;

INSERT INTO prod_order (order_no, product_code, product_name, planned_qty, line_code, status, created_by)
VALUES
('MO202406001', 'AMOLED_65', 'AMOLED 6.5寸柔性屏', 1000, 'LINE_01', 'RELEASED', 'system'),
('MO202406002', 'AMOLED_67', 'AMOLED 6.7寸柔性屏', 800, 'LINE_01', 'RELEASED', 'system')
ON CONFLICT (order_no) DO NOTHING;

-- Lot数据（10条，不同状态分布）
INSERT INTO prod_lot (lot_no, order_no, product_code, qty, current_step_code, current_equipment_code, status, hold_flag, created_by)
VALUES
('LOT202406001', 'MO202406001', 'AMOLED_65', 100, 'COATING', NULL, 'READY', 0, 'system'),
('LOT202406002', 'MO202406001', 'AMOLED_65', 100, 'COATING', NULL, 'READY', 0, 'system'),
('LOT202406003', 'MO202406001', 'AMOLED_65', 100, 'COATING', 'COATER_02', 'PROCESSING', 0, 'system'),
('LOT202406004', 'MO202406001', 'AMOLED_65', 100, 'EVAPORATION', 'EVAP_01', 'PROCESSING', 0, 'system'),
('LOT202406005', 'MO202406001', 'AMOLED_65', 100, 'ETCH', NULL, 'HOLD', 1, 'system'),
('LOT202406006', 'MO202406002', 'AMOLED_67', 80, 'COATING', NULL, 'HOLD', 1, 'system'),
('LOT202406007', 'MO202406001', 'AMOLED_65', 100, 'AGING', NULL, 'COMPLETED', 0, 'system'),
('LOT202406008', 'MO202406001', 'AMOLED_65', 100, 'AGING', NULL, 'COMPLETED', 0, 'system'),
('LOT202406009', 'MO202406002', 'AMOLED_67', 80, 'AGING', NULL, 'COMPLETED', 0, 'system'),
('LOT202406010', 'MO202406001', 'AMOLED_65', 100, 'COATING', NULL, 'READY', 0, 'system')
ON CONFLICT (lot_no) DO NOTHING;

INSERT INTO lot_hold_record (lot_no, hold_reason, hold_type, hold_by, hold_time, status)
VALUES
('LOT202406005', '蚀刻设备报警隔离，等待设备工程师确认', 'EQUIPMENT', 'system', CURRENT_TIMESTAMP - INTERVAL '2 hours', 'HOLD'),
('LOT202406006', '涂胶膜厚超限，质量工程师复判中', 'QUALITY', 'system', CURRENT_TIMESTAMP - INTERVAL '75 minutes', 'HOLD')
ON CONFLICT DO NOTHING;

INSERT INTO quality_inspection (
    inspection_no, lot_no, order_no, product_code, step_code, equipment_code, recipe_code,
    item_code, item_name, measured_value, upper_limit, lower_limit, unit, result,
    defect_code, defect_position, inspector, inspection_time, source, remark
)
VALUES
('QI-SEED-LOT202406006-001', 'LOT202406006', 'MO202406002', 'AMOLED_67', 'COATING', 'COATER_02', 'RCP_COAT_002',
 'THICKNESS', '涂胶厚度', 2.260000, 2.200000, 1.800000, 'μm', 'NG',
 'D-THICKNESS', 'COATING', 'system', CURRENT_TIMESTAMP - INTERVAL '76 minutes', 'SEED', '初始化种子数据：膜厚超上限'),
('QI-SEED-LOT202406004-001', 'LOT202406004', 'MO202406001', 'AMOLED_65', 'EVAPORATION', 'EVAP_01', 'RCP_EVAP_001',
 'VACUUM', '真空度', 0.000620, 0.000500, 0.000010, 'Pa', 'NG',
 'D-VACUUM', 'EVAPORATION', 'system', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'SEED', '初始化种子数据：真空度波动')
ON CONFLICT (inspection_no) DO NOTHING;

INSERT INTO quality_defect_record (
    defect_no, inspection_no, lot_no, step_code, equipment_code, defect_code, defect_name,
    defect_level, defect_position, qty, status, disposition, created_by
)
VALUES
('QD-SEED-LOT202406006-001', 'QI-SEED-LOT202406006-001', 'LOT202406006', 'COATING', 'COATER_02',
 'D-THICKNESS', '涂胶厚度超限', 'MAJOR', 'COATING', 1, 'OPEN', 'WAIT_MRB', 'system'),
('QD-SEED-LOT202406004-001', 'QI-SEED-LOT202406004-001', 'LOT202406004', 'EVAPORATION', 'EVAP_01',
 'D-VACUUM', '真空度超限', 'MAJOR', 'EVAPORATION', 1, 'OPEN', 'WAIT_MRB', 'system')
ON CONFLICT (defect_no) DO NOTHING;

INSERT INTO exception_event (
    event_no, event_type, event_level, lot_no, order_no, step_code, equipment_code,
    source_module, title, description, status, owner_role, occurred_time, created_by
)
VALUES
('EX-SEED-LOT202406006-001', 'QUALITY', 'P1', 'LOT202406006', 'MO202406002', 'COATING', 'COATER_02',
 'QUALITY', '涂胶膜厚超限', 'LOT202406006 在 COATING 工序涂胶厚度判定不合格', 'OPEN', 'QE', CURRENT_TIMESTAMP - INTERVAL '75 minutes', 'system'),
('EX-SEED-EVAP-001', 'EQUIPMENT', 'P2', 'LOT202406004', 'MO202406001', 'EVAPORATION', 'EVAP_01',
 'EAP_ADAPTER', '蒸镀真空度波动', 'EVAP_01 真空度高于 Recipe 上限，需设备工程师确认', 'PROCESSING', 'EE', CURRENT_TIMESTAMP - INTERVAL '35 minutes', 'system')
ON CONFLICT (event_no) DO NOTHING;

-- BOM与物料批次正式表
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
