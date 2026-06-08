ALTER TABLE quality_mrb_approval_task
    ADD COLUMN IF NOT EXISTS sla_level VARCHAR(30) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN IF NOT EXISTS sla_hours INT NOT NULL DEFAULT 4,
    ADD COLUMN IF NOT EXISTS escalation_role VARCHAR(50),
    ADD COLUMN IF NOT EXISTS escalated_to VARCHAR(80),
    ADD COLUMN IF NOT EXISTS escalated_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS escalation_reason VARCHAR(800),
    ADD COLUMN IF NOT EXISTS escalation_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_quality_mrb_approval_task_sla
ON quality_mrb_approval_task(approval_status, due_time, sla_level);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_approval_task_escalation
ON quality_mrb_approval_task(escalated_time, escalation_role);

UPDATE quality_mrb_approval_task
SET sla_level = CASE
        WHEN due_time IS NOT NULL AND due_time <= CURRENT_TIMESTAMP + INTERVAL '2 hours' THEN 'CRITICAL'
        ELSE COALESCE(NULLIF(sla_level, ''), 'STANDARD')
    END,
    sla_hours = COALESCE(NULLIF(sla_hours, 0), 4),
    escalation_role = COALESCE(escalation_role, CASE approval_role
        WHEN 'QE' THEN 'QUALITY_MANAGER'
        WHEN 'PE' THEN 'PROCESS_MANAGER'
        WHEN 'EE' THEN 'EQUIPMENT_MANAGER'
        ELSE 'MRB_CHAIR'
    END)
WHERE approval_status IN ('PENDING', 'ESCALATED');
