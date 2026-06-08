ALTER TABLE equipment_event
    ADD COLUMN IF NOT EXISTS reason_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS reason_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS downtime_category VARCHAR(40),
    ADD COLUMN IF NOT EXISTS downtime_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS started_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS ended_time TIMESTAMP,
    ADD COLUMN IF NOT EXISTS duration_minutes INT,
    ADD COLUMN IF NOT EXISTS impact_level VARCHAR(20);

CREATE INDEX IF NOT EXISTS idx_equipment_event_reason ON equipment_event(reason_code);
CREATE INDEX IF NOT EXISTS idx_equipment_event_downtime ON equipment_event(downtime_type, downtime_category);
CREATE INDEX IF NOT EXISTS idx_equipment_event_started ON equipment_event(started_time);

UPDATE equipment_event
SET started_time = COALESCE(started_time, occurred_time),
    downtime_category = COALESCE(downtime_category,
        CASE
            WHEN event_type = 'PM' THEN 'PM'
            WHEN event_type IN ('ALARM', 'PARAMETER', 'RECIPE') THEN 'EQUIPMENT'
            WHEN event_type = 'QUALITY' THEN 'QUALITY'
            ELSE 'STATUS'
        END
    ),
    downtime_type = COALESCE(downtime_type,
        CASE
            WHEN event_type = 'PM' THEN 'PLANNED'
            WHEN event_type IN ('STATUS') THEN 'STATE'
            ELSE 'UNPLANNED'
        END
    ),
    reason_code = COALESCE(reason_code, event_type),
    reason_name = COALESCE(reason_name, title),
    impact_level = COALESCE(impact_level, event_level)
WHERE started_time IS NULL
   OR downtime_category IS NULL
   OR downtime_type IS NULL
   OR reason_code IS NULL
   OR reason_name IS NULL
   OR impact_level IS NULL;

INSERT INTO equipment_event (
    event_no, equipment_code, line_code, event_type, event_level, lot_no, step_code, recipe_code,
    title, description, status, source_system, occurred_time, closed_by, closed_time, close_conclusion,
    created_by, created_time, updated_time, request_snapshot, reason_code, reason_name,
    downtime_category, downtime_type, started_time, ended_time, duration_minutes, impact_level
) VALUES
('EVT-260606-OEE-001', 'COATER_01', 'LINE_01', 'PM', 'P3', NULL, 'COATING', NULL,
 'Shift nozzle cleaning', 'Planned shift PM for coating nozzle and dispense pressure.', 'CLOSED',
 'eap-adapter', CURRENT_TIMESTAMP - INTERVAL '5 hours', 'ee1001', CURRENT_TIMESTAMP - INTERVAL '4 hours 25 minutes',
 'PM completed and equipment returned to IDLE.', 'system', CURRENT_TIMESTAMP - INTERVAL '5 hours',
 CURRENT_TIMESTAMP - INTERVAL '4 hours 25 minutes', '{"source":"seed-oee"}',
 'PM_NOZZLE_CLEAN', '喷嘴清洁', 'PM', 'PLANNED',
 CURRENT_TIMESTAMP - INTERVAL '5 hours', CURRENT_TIMESTAMP - INTERVAL '4 hours 25 minutes', 35, 'P3'),
('EVT-260606-OEE-002', 'ETCH_01', 'LINE_01', 'ALARM', 'P2', NULL, 'ETCH', NULL,
 'Etch chamber pressure alarm', 'Chamber pressure alarm caused unplanned equipment stop.', 'CLOSED',
 'eap-adapter', CURRENT_TIMESTAMP - INTERVAL '3 hours', 'ee1002', CURRENT_TIMESTAMP - INTERVAL '2 hours 32 minutes',
 'Pressure controller reset and dry-run passed.', 'system', CURRENT_TIMESTAMP - INTERVAL '3 hours',
 CURRENT_TIMESTAMP - INTERVAL '2 hours 32 minutes', '{"source":"seed-oee"}',
 'CHAMBER_PRESSURE', '腔体压力报警', 'EQUIPMENT', 'UNPLANNED',
 CURRENT_TIMESTAMP - INTERVAL '3 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours 32 minutes', 28, 'P2'),
('EVT-260606-OEE-003', 'EVAP_01', 'LINE_01', 'DOWN', 'P1', 'LOT202406004', 'EVAPORATION', 'RCP_EVAP_001',
 'Vacuum pump down', 'Vacuum pump status triggered unplanned downtime and blocks next Track In.', 'OPEN',
 'eap-adapter', CURRENT_TIMESTAMP - INTERVAL '38 minutes', NULL, NULL, NULL, 'system',
 CURRENT_TIMESTAMP - INTERVAL '38 minutes', CURRENT_TIMESTAMP - INTERVAL '38 minutes', '{"source":"seed-oee"}',
 'VACUUM_PUMP_DOWN', '真空泵停机', 'EQUIPMENT', 'UNPLANNED',
 CURRENT_TIMESTAMP - INTERVAL '38 minutes', NULL, NULL, 'P1')
ON CONFLICT (event_no) DO NOTHING;
