INSERT INTO sys_user (username, password, real_name, role, status)
VALUES
('admin', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '系统管理员', 'ADMIN', 1),
('planner', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '生产计划员', 'PLANNER', 1),
('operator', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '产线操作员', 'OPERATOR', 1),
('qe', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '质量工程师', 'QE', 1),
('pe', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '工艺工程师', 'PE', 1),
('ee', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '设备工程师', 'EE', 1),
('engineer', '$2a$10$TAPoLcedpCkE0idP1y29jOecDfIg8/Cj5KXbHMvyK2klxVMiwIESS', '工艺工程师', 'PE', 1)
ON CONFLICT (username) DO UPDATE SET
    password = EXCLUDED.password,
    real_name = EXCLUDED.real_name,
    role = EXCLUDED.role,
    status = EXCLUDED.status,
    updated_time = CURRENT_TIMESTAMP;
