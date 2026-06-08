ALTER TABLE md_bom_change_request
    ADD COLUMN IF NOT EXISTS eco_no VARCHAR(60),
    ADD COLUMN IF NOT EXISTS eco_risk_level VARCHAR(20) DEFAULT 'MEDIUM',
    ADD COLUMN IF NOT EXISTS eco_package_snapshot TEXT,
    ADD COLUMN IF NOT EXISTS eco_approval_status VARCHAR(20) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS eco_required_roles VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_bom_change_eco_no
ON md_bom_change_request(eco_no);

CREATE INDEX IF NOT EXISTS idx_bom_change_eco_approval_status
ON md_bom_change_request(eco_approval_status, status);

CREATE TABLE IF NOT EXISTS md_bom_eco_approval_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(60) NOT NULL UNIQUE,
    change_no VARCHAR(60) NOT NULL,
    eco_no VARCHAR(60),
    product_code VARCHAR(50),
    target_bom_code VARCHAR(50),
    approval_role VARCHAR(30) NOT NULL,
    approver VARCHAR(50),
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision VARCHAR(20),
    opinion VARCHAR(500),
    sla_level VARCHAR(20),
    sla_hours INT DEFAULT 24,
    due_time TIMESTAMP,
    action_time TIMESTAMP,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_eco_approval_change
        FOREIGN KEY (change_no) REFERENCES md_bom_change_request(change_no)
);

CREATE INDEX IF NOT EXISTS idx_bom_eco_approval_change
ON md_bom_eco_approval_task(change_no, approval_status, due_time);

CREATE INDEX IF NOT EXISTS idx_bom_eco_approval_role
ON md_bom_eco_approval_task(approval_role, approval_status, due_time);

UPDATE md_bom_change_request
SET eco_no = COALESCE(eco_no, change_no),
    eco_risk_level = COALESCE(eco_risk_level, 'MEDIUM'),
    eco_approval_status = CASE
        WHEN status = 'APPROVED' THEN 'APPROVED'
        WHEN status = 'REJECTED' THEN 'REJECTED'
        WHEN status = 'PUBLISHED' THEN 'APPROVED'
        ELSE COALESCE(eco_approval_status, 'PENDING')
    END,
    eco_required_roles = COALESCE(eco_required_roles, 'PE,QE,PLANNER'),
    eco_package_snapshot = COALESCE(eco_package_snapshot, after_snapshot)
WHERE eco_no IS NULL
   OR eco_risk_level IS NULL
   OR eco_approval_status IS NULL
   OR eco_required_roles IS NULL
   OR eco_package_snapshot IS NULL;
