CREATE TABLE IF NOT EXISTS quality_mrb_approval_task (
    id BIGSERIAL PRIMARY KEY,
    task_no VARCHAR(80) NOT NULL UNIQUE,
    mrb_no VARCHAR(80) NOT NULL,
    event_no VARCHAR(60) NOT NULL,
    lot_no VARCHAR(50),
    approval_role VARCHAR(50) NOT NULL,
    approver VARCHAR(80),
    approval_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    decision VARCHAR(30),
    opinion VARCHAR(800),
    due_time TIMESTAMP,
    action_time TIMESTAMP,
    created_by VARCHAR(80),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_approval_task_mrb
ON quality_mrb_approval_task(mrb_no, approval_status);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_approval_task_event
ON quality_mrb_approval_task(event_no, approval_status);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_approval_task_role
ON quality_mrb_approval_task(approval_role, approval_status, due_time);

INSERT INTO quality_mrb_approval_task (
    task_no, mrb_no, event_no, lot_no, approval_role, approver,
    approval_status, decision, opinion, due_time, action_time, created_by
)
VALUES
('MRBT-SEED-LOT202406006-QE', 'MRB-SEED-LOT202406006-001', 'EX-SEED-LOT202406006-001',
 'LOT202406006', 'QE', 'qe1003', 'APPROVED', 'APPROVE',
 '质量复测记录已补齐，继续保持 Hold 等待工艺确认。', CURRENT_TIMESTAMP + INTERVAL '4 hours',
 CURRENT_TIMESTAMP - INTERVAL '50 minutes', 'system'),
('MRBT-SEED-LOT202406006-PE', 'MRB-SEED-LOT202406006-001', 'EX-SEED-LOT202406006-001',
 'LOT202406006', 'PE', NULL, 'PENDING', NULL,
 NULL, CURRENT_TIMESTAMP + INTERVAL '4 hours', NULL, 'system')
ON CONFLICT (task_no) DO NOTHING;
