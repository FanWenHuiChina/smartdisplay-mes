ALTER TABLE equipment_gateway_connection
    ADD COLUMN IF NOT EXISTS driver_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS driver_mode VARCHAR(20) NOT NULL DEFAULT 'SIMULATED',
    ADD COLUMN IF NOT EXISTS tls_enabled SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS connection_timeout_ms INT NOT NULL DEFAULT 3000,
    ADD COLUMN IF NOT EXISTS read_timeout_ms INT NOT NULL DEFAULT 5000,
    ADD COLUMN IF NOT EXISTS driver_config_snapshot TEXT;

ALTER TABLE equipment_gateway_message
    ADD COLUMN IF NOT EXISTS driver_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS normalized_payload_snapshot TEXT;

CREATE INDEX IF NOT EXISTS idx_equipment_gateway_driver ON equipment_gateway_connection(driver_code);
CREATE INDEX IF NOT EXISTS idx_equipment_gateway_msg_driver ON equipment_gateway_message(driver_code);

UPDATE equipment_gateway_connection
SET driver_code = CASE protocol_type
    WHEN 'SECS_GEM' THEN 'secs-gem-driver'
    WHEN 'OPC_UA' THEN 'opc-ua-driver'
    WHEN 'VENDOR_HTTP' THEN 'vendor-http-driver'
    ELSE 'simulated-http-driver'
END
WHERE driver_code IS NULL OR driver_code = '';

UPDATE equipment_gateway_connection
SET driver_config_snapshot = json_build_object(
    'driverCode', driver_code,
    'driverMode', driver_mode,
    'tlsEnabled', tls_enabled,
    'connectionTimeoutMs', connection_timeout_ms,
    'readTimeoutMs', read_timeout_ms,
    'source', 'V1.29 default driver config'
)::TEXT
WHERE driver_config_snapshot IS NULL OR driver_config_snapshot = '';
