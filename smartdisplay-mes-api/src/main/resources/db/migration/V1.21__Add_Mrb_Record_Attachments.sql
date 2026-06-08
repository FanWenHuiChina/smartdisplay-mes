CREATE TABLE IF NOT EXISTS quality_mrb_record (
    id BIGSERIAL PRIMARY KEY,
    mrb_no VARCHAR(80) NOT NULL UNIQUE,
    event_no VARCHAR(60) NOT NULL,
    lot_no VARCHAR(50),
    review_type VARCHAR(30) NOT NULL,
    disposition_action VARCHAR(40),
    opinion VARCHAR(1000),
    meeting_no VARCHAR(80),
    participants VARCHAR(500),
    risk_level VARCHAR(20) NOT NULL DEFAULT 'P2',
    approval_status VARCHAR(30) NOT NULL DEFAULT 'APPROVED',
    reviewer VARCHAR(80),
    review_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attachment_count INT NOT NULL DEFAULT 0,
    request_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_record_event_time
ON quality_mrb_record(event_no, review_time DESC);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_record_lot_time
ON quality_mrb_record(lot_no, review_time DESC);

CREATE TABLE IF NOT EXISTS quality_mrb_attachment (
    id BIGSERIAL PRIMARY KEY,
    attachment_no VARCHAR(80) NOT NULL UNIQUE,
    mrb_no VARCHAR(80) NOT NULL,
    event_no VARCHAR(60) NOT NULL,
    lot_no VARCHAR(50),
    file_name VARCHAR(200) NOT NULL,
    file_url VARCHAR(500),
    file_hash VARCHAR(160),
    file_type VARCHAR(40),
    uploaded_by VARCHAR(80),
    uploaded_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_attachment_mrb
ON quality_mrb_attachment(mrb_no);

CREATE INDEX IF NOT EXISTS idx_quality_mrb_attachment_event
ON quality_mrb_attachment(event_no);

INSERT INTO quality_mrb_record (
    mrb_no, event_no, lot_no, review_type, disposition_action, opinion,
    meeting_no, participants, risk_level, approval_status, reviewer,
    review_time, attachment_count, request_snapshot
)
VALUES
('MRB-SEED-LOT202406006-001', 'EX-SEED-LOT202406006-001', 'LOT202406006',
 'REVIEW', 'CONTINUE_HOLD', '膜厚超上限，等待补充涂胶头点检记录后复判。',
 'MRB-20240606-001', 'qe1003,pe2007,ee3002', 'P1', 'APPROVED', 'qe1003',
 CURRENT_TIMESTAMP - INTERVAL '60 minutes', 1, '{}')
ON CONFLICT (mrb_no) DO NOTHING;

INSERT INTO quality_mrb_attachment (
    attachment_no, mrb_no, event_no, lot_no, file_name, file_url,
    file_hash, file_type, uploaded_by, uploaded_time
)
VALUES
('MRBA-SEED-LOT202406006-001', 'MRB-SEED-LOT202406006-001', 'EX-SEED-LOT202406006-001',
 'LOT202406006', 'COATER_02膜厚复测记录.xlsx', 'qms://mrb/MRB-20240606-001/recheck.xlsx',
 'sha256:seed-mrb-recheck', 'RECHECK', 'qe1003', CURRENT_TIMESTAMP - INTERVAL '58 minutes')
ON CONFLICT (attachment_no) DO NOTHING;
