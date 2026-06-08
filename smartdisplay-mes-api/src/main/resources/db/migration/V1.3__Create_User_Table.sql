-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    real_name VARCHAR(100),
    role VARCHAR(20) DEFAULT 'operator',
    status SMALLINT DEFAULT 1,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user IS '系统用户表';
COMMENT ON COLUMN sys_user.username IS '用户名';
COMMENT ON COLUMN sys_user.password IS '密码（加密）';
COMMENT ON COLUMN sys_user.real_name IS '真实姓名';
COMMENT ON COLUMN sys_user.role IS '角色：admin/engineer/operator';
COMMENT ON COLUMN sys_user.status IS '状态：1启用 0禁用';

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);

-- 插入测试用户（密码都是：123456）
INSERT INTO sys_user (username, password, real_name, role) VALUES
('admin', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '系统管理员', 'admin'),
('engineer', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '工程师', 'engineer'),
('operator', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '操作员', 'operator')
ON CONFLICT (username) DO NOTHING;
