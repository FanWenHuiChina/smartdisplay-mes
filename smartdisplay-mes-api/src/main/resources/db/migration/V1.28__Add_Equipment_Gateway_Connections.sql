CREATE TABLE IF NOT EXISTS equipment_gateway_connection (
    id BIGSERIAL PRIMARY KEY,
    gateway_code VARCHAR(50) NOT NULL UNIQUE,
    gateway_name VARCHAR(100) NOT NULL,
    protocol_type VARCHAR(30) NOT NULL,
    endpoint_uri VARCHAR(255) NOT NULL,
    line_code VARCHAR(50),
    equipment_codes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED',
    heartbeat_interval_ms INT NOT NULL DEFAULT 1000,
    last_heartbeat_time TIMESTAMP,
    last_error VARCHAR(500),
    enabled SMALLINT NOT NULL DEFAULT 1,
    created_by VARCHAR(50),
    updated_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_snapshot TEXT
);

CREATE INDEX IF NOT EXISTS idx_equipment_gateway_protocol ON equipment_gateway_connection(protocol_type);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_status ON equipment_gateway_connection(status);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_line ON equipment_gateway_connection(line_code);

CREATE TABLE IF NOT EXISTS equipment_gateway_message (
    id BIGSERIAL PRIMARY KEY,
    message_no VARCHAR(50) NOT NULL UNIQUE,
    gateway_code VARCHAR(50) NOT NULL,
    equipment_code VARCHAR(50),
    protocol_type VARCHAR(30) NOT NULL,
    direction VARCHAR(20) NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    correlation_id VARCHAR(100),
    process_status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    error_message VARCHAR(500),
    occurred_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_time TIMESTAMP,
    payload_snapshot TEXT,
    response_snapshot TEXT,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_equipment_gateway_msg_gateway ON equipment_gateway_message(gateway_code);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_msg_equipment ON equipment_gateway_message(equipment_code);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_msg_time ON equipment_gateway_message(occurred_time);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_msg_status ON equipment_gateway_message(process_status);

INSERT INTO equipment_gateway_connection (
    gateway_code, gateway_name, protocol_type, endpoint_uri, line_code, equipment_codes,
    status, heartbeat_interval_ms, last_heartbeat_time, created_by, updated_by, request_snapshot
) VALUES
('GW-SIM-HTTP-01', '试点模拟EAP网关', 'SIMULATED_HTTP', 'http://localhost:8080/api/v1/adapters/eap/messages',
 'LINE_01', '["COATER_01","COATER_02","EVAP_01","INSPECT_01"]',
 'CONNECTED', 1000, CURRENT_TIMESTAMP - INTERVAL '1 minute', 'system', 'system', '{"source":"seed-gateway"}'),
('GW-SECSGEM-PLACEHOLDER', 'SECS/GEM预留网关', 'SECS_GEM', 'secs://192.168.10.20:5000',
 'LINE_01', '["EVAP_01"]',
 'DISCONNECTED', 500, NULL, 'system', 'system', '{"source":"seed-gateway","placeholder":true}'),
('GW-OPCUA-PLACEHOLDER', 'OPC UA预留网关', 'OPC_UA', 'opc.tcp://192.168.10.30:4840',
 'LINE_01', '["COATER_01","COATER_02"]',
 'DISCONNECTED', 1000, NULL, 'system', 'system', '{"source":"seed-gateway","placeholder":true}')
ON CONFLICT (gateway_code) DO NOTHING;

INSERT INTO equipment_gateway_message (
    message_no, gateway_code, equipment_code, protocol_type, direction, message_type,
    correlation_id, process_status, occurred_time, processed_time, payload_snapshot, response_snapshot
) VALUES
('EGM-260606-001', 'GW-SIM-HTTP-01', 'COATER_01', 'SIMULATED_HTTP', 'INBOUND', 'STATUS',
 'SEED-STATUS-001', 'PROCESSED', CURRENT_TIMESTAMP - INTERVAL '3 minutes', CURRENT_TIMESTAMP - INTERVAL '3 minutes',
 '{"messageType":"STATUS","equipmentCode":"COATER_01","status":"RUNNING"}', '{"result":"OK"}'),
('EGM-260606-002', 'GW-SIM-HTTP-01', 'COATER_02', 'SIMULATED_HTTP', 'INBOUND', 'CYCLE',
 'SEED-CYCLE-001', 'PROCESSED', CURRENT_TIMESTAMP - INTERVAL '2 minutes', CURRENT_TIMESTAMP - INTERVAL '2 minutes',
 '{"messageType":"CYCLE","equipmentCode":"COATER_02","actualCycleSeconds":72}', '{"result":"NG"}')
ON CONFLICT (message_no) DO NOTHING;
