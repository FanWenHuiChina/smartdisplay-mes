CREATE TABLE IF NOT EXISTS quality_mrb_minutes (
    id BIGSERIAL PRIMARY KEY,
    minutes_no VARCHAR(80) NOT NULL UNIQUE,
    mrb_no VARCHAR(80) NOT NULL,
    event_no VARCHAR(60) NOT NULL,
    lot_no VARCHAR(50),
    version_no INT NOT NULL,
    minutes_content TEXT NOT NULL,
    summary VARCHAR(1000),
    action_items TEXT,
    risk_note VARCHAR(1000),
    editor VARCHAR(80),
    edit_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_reason VARCHAR(500),
    source_action VARCHAR(40),
    request_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quality_mrb_minutes_version UNIQUE (mrb_no, version_no)
);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_minutes_mrb_version
ON quality_mrb_minutes(mrb_no, version_no DESC);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_minutes_event
ON quality_mrb_minutes(event_no, edit_time DESC);

INSERT INTO quality_mrb_minutes (
    minutes_no, mrb_no, event_no, lot_no, version_no, minutes_content,
    summary, action_items, risk_note, editor, edit_time, change_reason,
    source_action, request_snapshot
)
VALUES (
    'MRBM-SEED-LOT202406006-001-V1',
    'MRB-SEED-LOT202406006-001',
    'EX-SEED-LOT202406006-001',
    'LOT202406006',
    1,
    '会议结论：COATER_02 膜厚复测仍高于上限，继续 Hold，补充喷嘴清洁记录和同批 Lot 影响面分析后再复判。',
    '继续 Hold，补充复测与设备清洁证据。',
    'QE补充复测记录；PE确认涂胶参数窗口；EE确认喷嘴清洁和设备状态。',
    'P1异常，未完成复判前禁止放行。',
    'qe1003',
    CURRENT_TIMESTAMP - INTERVAL '57 minutes',
    '初始化种子纪要',
    'REVIEW',
    '{}'
)
ON CONFLICT (minutes_no) DO NOTHING;
