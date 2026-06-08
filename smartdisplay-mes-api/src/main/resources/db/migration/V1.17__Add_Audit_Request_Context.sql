ALTER TABLE sys_audit_log
    ADD COLUMN IF NOT EXISTS request_method VARCHAR(20),
    ADD COLUMN IF NOT EXISTS request_uri VARCHAR(300),
    ADD COLUMN IF NOT EXISTS client_ip VARCHAR(80),
    ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);

COMMENT ON COLUMN sys_audit_log.request_method IS '请求方法';
COMMENT ON COLUMN sys_audit_log.request_uri IS '请求URI';
COMMENT ON COLUMN sys_audit_log.client_ip IS '客户端IP';
COMMENT ON COLUMN sys_audit_log.user_agent IS '浏览器或调用端标识';

CREATE INDEX IF NOT EXISTS idx_audit_request_uri ON sys_audit_log(request_uri);
