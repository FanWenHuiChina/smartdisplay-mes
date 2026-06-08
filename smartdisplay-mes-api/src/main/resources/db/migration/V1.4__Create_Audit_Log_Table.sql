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

COMMENT ON TABLE sys_audit_log IS '系统审计日志表';
COMMENT ON COLUMN sys_audit_log.action IS '操作动作';
COMMENT ON COLUMN sys_audit_log.biz_no IS '业务对象编号';
COMMENT ON COLUMN sys_audit_log.biz_type IS '业务对象类型';
COMMENT ON COLUMN sys_audit_log.description IS '操作描述';
COMMENT ON COLUMN sys_audit_log.operator IS '操作人';
COMMENT ON COLUMN sys_audit_log.result IS '操作结果';
COMMENT ON COLUMN sys_audit_log.source IS '来源终端或适配器';
COMMENT ON COLUMN sys_audit_log.request_snapshot IS '输入快照';

CREATE INDEX IF NOT EXISTS idx_audit_biz_no ON sys_audit_log(biz_no);
CREATE INDEX IF NOT EXISTS idx_audit_action ON sys_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_created_time ON sys_audit_log(created_time);
