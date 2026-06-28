UPDATE equipment_gateway_message
SET gateway_code = 'GW-SECSGEM-SHADOW'
WHERE gateway_code = 'GW-SECSGEM-PLACEHOLDER'
  AND NOT EXISTS (
      SELECT 1 FROM equipment_gateway_connection WHERE gateway_code = 'GW-SECSGEM-SHADOW'
  );

UPDATE equipment_gateway_health_check
SET gateway_code = 'GW-SECSGEM-SHADOW',
    error_message = 'SECS_GEM shadow protocol frame validation ready; real equipment handshake not configured',
    request_snapshot = '{"source":"seed-health","mode":"SHADOW"}',
    response_snapshot = '{"message":"SECS_GEM shadow protocol frame validation ready; real equipment handshake not configured","requiresRealEquipmentLink":true,"driverMode":"SHADOW"}'
WHERE gateway_code = 'GW-SECSGEM-PLACEHOLDER'
  AND NOT EXISTS (
      SELECT 1 FROM equipment_gateway_connection WHERE gateway_code = 'GW-SECSGEM-SHADOW'
  );

UPDATE equipment_gateway_connection
SET gateway_code = 'GW-SECSGEM-SHADOW',
    gateway_name = 'SECS/GEM影子协议网关',
    driver_mode = 'SHADOW',
    status = CASE WHEN status = 'DISCONNECTED' THEN 'DEGRADED' ELSE status END,
    last_error = 'SECS_GEM shadow protocol frame validation ready; real equipment handshake not configured',
    request_snapshot = '{"source":"seed-gateway","mode":"SHADOW","boundary":"frame validation only; no live equipment handshake"}'
WHERE gateway_code = 'GW-SECSGEM-PLACEHOLDER'
  AND NOT EXISTS (
      SELECT 1 FROM equipment_gateway_connection WHERE gateway_code = 'GW-SECSGEM-SHADOW'
  );

UPDATE equipment_gateway_connection
SET gateway_name = 'SECS/GEM影子协议网关',
    driver_mode = 'SHADOW',
    driver_config_snapshot = json_build_object(
        'driverCode', COALESCE(NULLIF(driver_code, ''), 'secs-gem-driver'),
        'driverMode', 'SHADOW',
        'protocolType', protocol_type,
        'endpointUri', endpoint_uri,
        'tlsEnabled', tls_enabled,
        'connectionTimeoutMs', connection_timeout_ms,
        'readTimeoutMs', read_timeout_ms,
        'protocolFrameValidation', true,
        'boundary', 'frame validation only; no live equipment handshake'
    )::TEXT
WHERE gateway_code = 'GW-SECSGEM-SHADOW';

UPDATE equipment_gateway_message
SET gateway_code = 'GW-OPCUA-SHADOW'
WHERE gateway_code = 'GW-OPCUA-PLACEHOLDER'
  AND NOT EXISTS (
      SELECT 1 FROM equipment_gateway_connection WHERE gateway_code = 'GW-OPCUA-SHADOW'
  );

UPDATE equipment_gateway_health_check
SET gateway_code = 'GW-OPCUA-SHADOW',
    error_message = 'OPC_UA shadow protocol frame validation ready; real equipment handshake not configured',
    request_snapshot = '{"source":"seed-health","mode":"SHADOW"}',
    response_snapshot = '{"message":"OPC_UA shadow protocol frame validation ready; real equipment handshake not configured","requiresRealEquipmentLink":true,"driverMode":"SHADOW"}'
WHERE gateway_code = 'GW-OPCUA-PLACEHOLDER'
  AND NOT EXISTS (
      SELECT 1 FROM equipment_gateway_connection WHERE gateway_code = 'GW-OPCUA-SHADOW'
  );

UPDATE equipment_gateway_connection
SET gateway_code = 'GW-OPCUA-SHADOW',
    gateway_name = 'OPC UA影子协议网关',
    driver_mode = 'SHADOW',
    status = CASE WHEN status = 'DISCONNECTED' THEN 'DEGRADED' ELSE status END,
    last_error = 'OPC_UA shadow protocol frame validation ready; real equipment handshake not configured',
    request_snapshot = '{"source":"seed-gateway","mode":"SHADOW","boundary":"frame validation only; no live equipment handshake"}'
WHERE gateway_code = 'GW-OPCUA-PLACEHOLDER'
  AND NOT EXISTS (
      SELECT 1 FROM equipment_gateway_connection WHERE gateway_code = 'GW-OPCUA-SHADOW'
  );

UPDATE equipment_gateway_connection
SET gateway_name = 'OPC UA影子协议网关',
    driver_mode = 'SHADOW',
    driver_config_snapshot = json_build_object(
        'driverCode', COALESCE(NULLIF(driver_code, ''), 'opc-ua-driver'),
        'driverMode', 'SHADOW',
        'protocolType', protocol_type,
        'endpointUri', endpoint_uri,
        'tlsEnabled', tls_enabled,
        'connectionTimeoutMs', connection_timeout_ms,
        'readTimeoutMs', read_timeout_ms,
        'protocolFrameValidation', true,
        'boundary', 'frame validation only; no live equipment handshake'
    )::TEXT
WHERE gateway_code = 'GW-OPCUA-SHADOW';
