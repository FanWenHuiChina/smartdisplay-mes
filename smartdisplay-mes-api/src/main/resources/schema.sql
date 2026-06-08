-- SmartDisplay MES 数据库初始化脚本
-- 参考显示行业公开资料与通用MES模型设计

-- ====================================
-- 1. Recipe配方管理
-- ====================================

-- Recipe主表
CREATE TABLE md_recipe (
    id BIGSERIAL PRIMARY KEY,
    recipe_code VARCHAR(50) NOT NULL UNIQUE COMMENT 'Recipe编码',
    recipe_name VARCHAR(100) NOT NULL COMMENT 'Recipe名称',
    product_code VARCHAR(50) NOT NULL COMMENT '产品型号',
    step_code VARCHAR(50) NOT NULL COMMENT '工序编码',
    equipment_code VARCHAR(50) NOT NULL COMMENT '设备编码',
    recipe_version VARCHAR(20) NOT NULL COMMENT 'Recipe版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT-草稿, ACTIVE-生效, INACTIVE-失效',
    description TEXT COMMENT '描述',
    created_by VARCHAR(50) COMMENT '创建人',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_by VARCHAR(50) COMMENT '更新人',
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted SMALLINT DEFAULT 0 COMMENT '逻辑删除: 0-未删除, 1-已删除'
);

COMMENT ON TABLE md_recipe IS 'Recipe配方主表';

-- 唯一索引: 产品+工序+设备+版本唯一
CREATE UNIQUE INDEX uk_recipe_product_step_equip_ver
ON md_recipe(product_code, step_code, equipment_code, recipe_version)
WHERE deleted = 0;

-- 索引优化
CREATE INDEX idx_recipe_product ON md_recipe(product_code);
CREATE INDEX idx_recipe_step ON md_recipe(step_code);
CREATE INDEX idx_recipe_equipment ON md_recipe(equipment_code);
CREATE INDEX idx_recipe_status ON md_recipe(status);

-- Recipe参数表
CREATE TABLE md_recipe_param (
    id BIGSERIAL PRIMARY KEY,
    recipe_id BIGINT NOT NULL COMMENT 'Recipe主表ID',
    param_name VARCHAR(100) NOT NULL COMMENT '参数名称',
    param_code VARCHAR(50) COMMENT '参数编码',
    target_value DECIMAL(18,4) COMMENT '目标值',
    upper_limit DECIMAL(18,4) COMMENT '上限',
    lower_limit DECIMAL(18,4) COMMENT '下限',
    unit VARCHAR(20) COMMENT '单位',
    param_type VARCHAR(20) COMMENT '参数类型: TEMPERATURE-温度, PRESSURE-压力, TIME-时间, SPEED-速度',
    is_key_param SMALLINT DEFAULT 0 COMMENT '是否关键参数: 0-否, 1-是',
    display_order INT COMMENT '显示顺序',
    remark VARCHAR(200) COMMENT '备注',
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    CONSTRAINT fk_recipe_param_recipe FOREIGN KEY (recipe_id) REFERENCES md_recipe(id) ON DELETE CASCADE
);

COMMENT ON TABLE md_recipe_param IS 'Recipe参数表';

-- 索引
CREATE INDEX idx_recipe_param_recipe_id ON md_recipe_param(recipe_id);
CREATE INDEX idx_recipe_param_name ON md_recipe_param(param_name);

-- ====================================
-- 2. 插入测试数据
-- ====================================

-- 插入Recipe示例 (AMOLED 6.5寸 - 涂胶工序)
INSERT INTO md_recipe (recipe_code, recipe_name, product_code, step_code, equipment_code, recipe_version, status, description, created_by)
VALUES
('RCP_COAT_001', 'AMOLED 6.5寸涂胶Recipe', 'AMOLED_65', 'COATING', 'COATER_01', 'V1.0', 'ACTIVE',
 '用于AMOLED 6.5寸产品的PI涂胶工序标准参数', 'system');

-- 插入Recipe参数
INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT
    id,
    '涂胶温度',
    'TEMP_COATING',
    150.0,
    155.0,
    145.0,
    '℃',
    'TEMPERATURE',
    1,
    1
FROM md_recipe WHERE recipe_code = 'RCP_COAT_001';

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT
    id,
    '涂胶速度',
    'SPEED_COATING',
    300.0,
    320.0,
    280.0,
    'mm/s',
    'SPEED',
    1,
    2
FROM md_recipe WHERE recipe_code = 'RCP_COAT_001';

INSERT INTO md_recipe_param (recipe_id, param_name, param_code, target_value, upper_limit, lower_limit, unit, param_type, is_key_param, display_order)
SELECT
    id,
    '涂胶时间',
    'TIME_COATING',
    30.0,
    35.0,
    25.0,
    's',
    'TIME',
    0,
    3
FROM md_recipe WHERE recipe_code = 'RCP_COAT_001';
