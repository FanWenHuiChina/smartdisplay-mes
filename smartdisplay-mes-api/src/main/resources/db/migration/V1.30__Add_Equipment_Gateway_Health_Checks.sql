CREATE TABLE IF NOT EXISTS equipment_gateway_health_check (
    id BIGSERIAL PRIMARY KEY,
    check_no VARCHAR(50) NOT NULL UNIQUE,
    gateway_code VARCHAR(50) NOT NULL,
    protocol_type VARCHAR(30) NOT NULL,
    driver_code VARCHAR(50),
    endpoint_uri VARCHAR(255),
    check_type VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    result_status VARCHAR(20) NOT NULL,
    latency_ms INT,
    error_message VARCHAR(500),
    checked_by VARCHAR(50),
    checked_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT,
    response_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_gateway_health_gateway ON equipment_gateway_health_check(gateway_code);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_health_status ON equipment_gateway_health_check(result_status);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_health_time ON equipment_gateway_health_check(checked_time);

INSERT INTO equipment_gateway_health_check (
    check_no, gateway_code, protocol_type, driver_code, endpoint_uri, check_type,
    result_status, latency_ms, error_message, checked_by, checked_time, request_snapshot, response_snapshot
) VALUES
('EGH-260606-001', 'GW-SIM-HTTP-01', 'SIMULATED_HTTP', 'simulated-http-driver',
 'http://localhost:8080/api/v1/adapters/eap/messages', 'SEED',
 'PASS', 12, '', 'system', CURRENT_TIMESTAMP - INTERVAL '4 minutes',
 '{"source":"seed-health"}', '{"message":"simulated gateway reachable","requiresRealEquipmentLink":false}'),
('EGH-260606-002', 'GW-SECSGEM-PLACEHOLDER', 'SECS_GEM', 'secs-gem-driver',
 'secs://192.168.10.20:5000', 'SEED',
 'WARN', 0, 'real equipment link not enabled in pilot', 'system', CURRENT_TIMESTAMP - INTERVAL '3 minutes',
 '{"source":"seed-health","placeholder":true}', '{"message":"protocol boundary configured; real SECS/GEM handshake pending","requiresRealEquipmentLink":true}')
ON CONFLICT (check_no) DO NOTHING;
