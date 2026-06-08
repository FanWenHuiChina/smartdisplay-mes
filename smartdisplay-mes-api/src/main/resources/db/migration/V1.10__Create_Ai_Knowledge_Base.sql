CREATE TABLE IF NOT EXISTS ai_kb_document (
    id BIGSERIAL PRIMARY KEY,
    document_no VARCHAR(80) NOT NULL UNIQUE,
    document_name VARCHAR(160) NOT NULL,
    document_type VARCHAR(40) NOT NULL,
    doc_version VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_uri VARCHAR(300),
    owner_role VARCHAR(50),
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_kb_chunk (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_no VARCHAR(100) NOT NULL UNIQUE,
    chunk_title VARCHAR(160) NOT NULL,
    chunk_seq INT NOT NULL,
    content TEXT NOT NULL,
    keywords VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_kb_chunk_document FOREIGN KEY (document_id) REFERENCES ai_kb_document(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_kb_document_type ON ai_kb_document(document_type, status);
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_document ON ai_kb_chunk(document_id);
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_status ON ai_kb_chunk(status);

INSERT INTO ai_kb_document (document_no, document_name, document_type, doc_version, status, source_uri, owner_role, created_by)
VALUES
('SOP-HOLD-001', 'AMOLED异常Hold与Release处置SOP', 'SOP', 'V1.0', 'ACTIVE', 'docs/sop/SOP-HOLD-001.md', 'QE', 'system'),
('MANUAL-EVAP-001', '蒸镀设备报警排查手册', 'EQUIPMENT_MANUAL', 'V1.0', 'ACTIVE', 'docs/manual/MANUAL-EVAP-001.md', 'EE', 'system'),
('QMS-MURA-001', 'Mura缺陷判定标准', 'QUALITY_STANDARD', 'V1.0', 'ACTIVE', 'docs/qms/QMS-MURA-001.md', 'QE', 'system'),
('SOP-MATERIAL-001', '关键物料批次追溯SOP', 'SOP', 'V1.0', 'ACTIVE', 'docs/sop/SOP-MATERIAL-001.md', 'PLANNER', 'system')
ON CONFLICT (document_no) DO UPDATE SET
    document_name = EXCLUDED.document_name,
    document_type = EXCLUDED.document_type,
    doc_version = EXCLUDED.doc_version,
    status = EXCLUDED.status,
    source_uri = EXCLUDED.source_uri,
    owner_role = EXCLUDED.owner_role,
    updated_time = CURRENT_TIMESTAMP;

INSERT INTO ai_kb_chunk (document_id, chunk_no, chunk_title, chunk_seq, content, keywords, status)
SELECT doc.id, item.chunk_no, item.chunk_title, item.chunk_seq, item.content, item.keywords, 'ACTIVE'
FROM ai_kb_document doc
JOIN (
    VALUES
    ('SOP-HOLD-001', 'SOP-HOLD-001-001', '异常隔离与Hold原则', 1, '发现质量NG、Recipe关键参数超限、设备报警影响当前Lot时，应先Hold Lot并隔离影响范围；记录原因、责任模块、处置建议和审批人，禁止自动放行。', 'hold release recipe 参数超限 设备报警 质量NG 隔离 审批'),
    ('SOP-HOLD-001', 'SOP-HOLD-001-002', 'Release放行条件', 2, 'Release必须有复判结论、处置说明和审批人；质量类Hold由QE确认，工艺类Hold由PE确认，设备类Hold由EE确认，放行后Lot回到可执行状态。', 'release 放行 复判 QE PE EE 审批 可执行'),
    ('SOP-HOLD-001', 'SOP-HOLD-001-003', 'Rework与Scrap边界', 3, 'Rework必须选择返工路线和返工起始工序；Scrap必须二次确认并记录报废原因、责任模块、审批人和时间。', 'rework scrap 返工 报废 路线 起始工序 二次确认'),
    ('MANUAL-EVAP-001', 'MANUAL-EVAP-001-001', '蒸镀真空波动排查', 1, 'EVAP设备发生真空波动时，先确认最近2小时报警趋势、真空泵状态、腔体压力曲线和PM后稳定时间；受影响Lot需结合近期Mura缺陷趋势评估风险。', 'EVAP 蒸镀 真空 波动 报警 真空泵 Mura PM'),
    ('MANUAL-EVAP-001', 'MANUAL-EVAP-001-002', '设备异常与Lot风险', 2, '设备报警未关闭前不建议新Lot进站；已加工Lot应通过AOI抽检、参数快照和设备事件时间窗判断是否需要Hold或MRB复判。', '设备异常 Lot 进站 AOI 参数快照 MRB Hold'),
    ('QMS-MURA-001', 'QMS-MURA-001-001', 'Mura缺陷判定', 1, 'Mura缺陷需结合AOI结果、缺陷位置、批次集中度和相关设备事件综合判定；连续Lot出现同类Mura时应升级为P1质量异常。', 'Mura 缺陷 AOI 位置 批次 设备事件 P1 质量异常'),
    ('SOP-MATERIAL-001', 'SOP-MATERIAL-001-001', '关键物料批次齐套', 1, 'Track In前必须校验关键物料批次齐套、质量状态PASS、可用量满足BOM需求；批次锁定后Track Out生成消耗追溯记录。', '物料 批次 齐套 Track In Track Out BOM PASS 消耗追溯')
) AS item(document_no, chunk_no, chunk_title, chunk_seq, content, keywords)
ON doc.document_no = item.document_no
ON CONFLICT (chunk_no) DO UPDATE SET
    chunk_title = EXCLUDED.chunk_title,
    chunk_seq = EXCLUDED.chunk_seq,
    content = EXCLUDED.content,
    keywords = EXCLUDED.keywords,
    status = EXCLUDED.status;
