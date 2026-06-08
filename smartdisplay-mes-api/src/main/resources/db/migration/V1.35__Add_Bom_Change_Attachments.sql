CREATE TABLE IF NOT EXISTS md_bom_change_attachment (
    id BIGSERIAL PRIMARY KEY,
    attachment_no VARCHAR(60) NOT NULL UNIQUE,
    change_no VARCHAR(60) NOT NULL,
    product_code VARCHAR(50),
    target_bom_code VARCHAR(50),
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(500),
    file_hash VARCHAR(128),
    file_type VARCHAR(50) DEFAULT 'SUBSTITUTE_VALIDATION',
    attachment_role VARCHAR(50) DEFAULT 'SUBSTITUTE_VALIDATION',
    uploaded_by VARCHAR(50),
    uploaded_time TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_bom_change_attachment_change
        FOREIGN KEY (change_no) REFERENCES md_bom_change_request(change_no)
);

CREATE INDEX IF NOT EXISTS idx_bom_change_attachment_change
ON md_bom_change_attachment(change_no, uploaded_time);

INSERT INTO md_bom_change_attachment (
    attachment_no, change_no, product_code, target_bom_code, file_name, file_url,
    file_hash, file_type, attachment_role, uploaded_by, uploaded_time
)
SELECT 'BCA-SEED-001', change_no, product_code, target_bom_code,
       'PI替代料试点验证报告.pdf', 'qms://bom-change/PI替代料试点验证报告.pdf',
       'sha256:bom-change-seed', 'SUBSTITUTE_VALIDATION', 'SUBSTITUTE_VALIDATION',
       COALESCE(requested_by, 'system'), COALESCE(requested_time, CURRENT_TIMESTAMP)
FROM md_bom_change_request
WHERE target_bom_code LIKE '%V07%'
ORDER BY requested_time DESC NULLS LAST, id DESC
LIMIT 1
ON CONFLICT (attachment_no) DO NOTHING;
