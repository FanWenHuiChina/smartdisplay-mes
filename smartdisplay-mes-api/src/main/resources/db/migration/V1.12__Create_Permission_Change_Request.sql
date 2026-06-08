CREATE TABLE IF NOT EXISTS sys_permission_change_request (
    id BIGSERIAL PRIMARY KEY,
    change_no VARCHAR(80) NOT NULL UNIQUE,
    target_role VARCHAR(50) NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    before_snapshot TEXT NOT NULL,
    after_snapshot TEXT NOT NULL,
    reason VARCHAR(500),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    requester VARCHAR(80) NOT NULL,
    reviewer VARCHAR(80),
    review_opinion VARCHAR(500),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_time TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_permission_change_request IS '权限变更申请单';
COMMENT ON COLUMN sys_permission_change_request.change_no IS '权限变更单号';
COMMENT ON COLUMN sys_permission_change_request.target_role IS '目标角色';
COMMENT ON COLUMN sys_permission_change_request.before_snapshot IS '变更前权限快照';
COMMENT ON COLUMN sys_permission_change_request.after_snapshot IS '变更后权限快照';
COMMENT ON COLUMN sys_permission_change_request.status IS '审批状态';

CREATE INDEX IF NOT EXISTS idx_permission_change_role ON sys_permission_change_request(target_role);
CREATE INDEX IF NOT EXISTS idx_permission_change_status ON sys_permission_change_request(status);
CREATE INDEX IF NOT EXISTS idx_permission_change_created_time ON sys_permission_change_request(created_time);
