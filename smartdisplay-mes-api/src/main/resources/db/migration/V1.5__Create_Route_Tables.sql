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
