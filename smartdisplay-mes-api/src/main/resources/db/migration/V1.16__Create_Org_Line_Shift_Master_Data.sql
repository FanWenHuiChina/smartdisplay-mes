CREATE TABLE IF NOT EXISTS md_site (
    id BIGSERIAL PRIMARY KEY,
    site_code VARCHAR(50) NOT NULL UNIQUE,
    site_name VARCHAR(100) NOT NULL,
    site_type VARCHAR(50),
    region VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS md_production_line (
    id BIGSERIAL PRIMARY KEY,
    line_code VARCHAR(50) NOT NULL UNIQUE,
    line_name VARCHAR(100) NOT NULL,
    site_code VARCHAR(50) NOT NULL,
    line_type VARCHAR(50),
    workshop VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT DEFAULT 0,
    description TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_md_production_line_site ON md_production_line(site_code);
CREATE INDEX IF NOT EXISTS idx_md_production_line_status ON md_production_line(status);

CREATE TABLE IF NOT EXISTS md_work_shift (
    id BIGSERIAL PRIMARY KEY,
    shift_code VARCHAR(50) NOT NULL UNIQUE,
    shift_name VARCHAR(100) NOT NULL,
    line_code VARCHAR(50) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    cross_day SMALLINT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_md_work_shift_line ON md_work_shift(line_code);
CREATE INDEX IF NOT EXISTS idx_md_work_shift_status ON md_work_shift(status);

INSERT INTO md_site (site_code, site_name, site_type, region, status, description)
VALUES
('SITE_HF_01', '合肥显示试点基地', 'DISPLAY_FAB', '华东', 'ACTIVE', '单基地生产级试点主数据')
ON CONFLICT (site_code) DO NOTHING;

INSERT INTO md_production_line (line_code, line_name, site_code, line_type, workshop, status, sort_order, description)
VALUES
('LINE_01', 'G6柔性AMOLED试点线', 'SITE_HF_01', 'G6_FLEX_AMOLED', 'Array-Cell-Module', 'ACTIVE', 10, '当前生产级试点单产线'),
('LINE_02', 'G6柔性AMOLED预留线', 'SITE_HF_01', 'G6_FLEX_AMOLED', 'Array-Cell-Module', 'PLANNED', 20, '用于后续多产线扩展验证')
ON CONFLICT (line_code) DO NOTHING;

INSERT INTO md_work_shift (shift_code, shift_name, line_code, start_time, end_time, cross_day, status, description)
VALUES
('SHIFT_D_LINE_01', 'LINE_01 白班', 'LINE_01', TIME '08:00', TIME '20:00', 0, 'ACTIVE', '试点线白班'),
('SHIFT_N_LINE_01', 'LINE_01 夜班', 'LINE_01', TIME '20:00', TIME '08:00', 1, 'ACTIVE', '试点线夜班'),
('SHIFT_D_LINE_02', 'LINE_02 白班', 'LINE_02', TIME '08:00', TIME '20:00', 0, 'PLANNED', '预留线白班')
ON CONFLICT (shift_code) DO NOTHING;
