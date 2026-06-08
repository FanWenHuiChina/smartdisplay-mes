CREATE TABLE IF NOT EXISTS ai_model_config (
    id BIGSERIAL PRIMARY KEY,
    config_code VARCHAR(80) NOT NULL UNIQUE,
    config_name VARCHAR(160) NOT NULL,
    use_case VARCHAR(50) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    model_mode VARCHAR(30) NOT NULL DEFAULT 'SIMULATED',
    endpoint_uri VARCHAR(300),
    prompt_template_version VARCHAR(50) NOT NULL,
    retrieval_strategy VARCHAR(50) NOT NULL DEFAULT 'NONE',
    temperature NUMERIC(6,4),
    max_tokens INT,
    timeout_ms INT NOT NULL DEFAULT 30000,
    enabled SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    model_config_snapshot TEXT,
    created_by VARCHAR(50),
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(50),
    updated_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_model_config IS 'AI模型运行配置表';
COMMENT ON COLUMN ai_model_config.config_code IS '配置编码';
COMMENT ON COLUMN ai_model_config.use_case IS '适用场景：良率日报/设备分析/SOP问答';
COMMENT ON COLUMN ai_model_config.provider IS '模型供应方';
COMMENT ON COLUMN ai_model_config.model_mode IS '模型模式：SIMULATED/SHADOW/EXTERNAL';
COMMENT ON COLUMN ai_model_config.retrieval_strategy IS '检索策略';
COMMENT ON COLUMN ai_model_config.model_config_snapshot IS '模型配置快照JSON';

ALTER TABLE ai_report_record
    ADD COLUMN IF NOT EXISTS model_provider VARCHAR(50),
    ADD COLUMN IF NOT EXISTS model_mode VARCHAR(30),
    ADD COLUMN IF NOT EXISTS model_config_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS retrieval_strategy VARCHAR(50),
    ADD COLUMN IF NOT EXISTS evidence_count INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_evidence_score NUMERIC(8,4) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS evidence_level VARCHAR(20) NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS insufficient_evidence SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS model_config_snapshot TEXT;

ALTER TABLE ai_kb_chunk
    ADD COLUMN IF NOT EXISTS retrieval_strategy VARCHAR(50) NOT NULL DEFAULT 'KEYWORD_FALLBACK',
    ADD COLUMN IF NOT EXISTS embedding_status VARCHAR(30) NOT NULL DEFAULT 'NOT_INDEXED',
    ADD COLUMN IF NOT EXISTS embedding_model VARCHAR(120),
    ADD COLUMN IF NOT EXISTS embedding_ref VARCHAR(180),
    ADD COLUMN IF NOT EXISTS last_indexed_time TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_ai_model_config_use_case ON ai_model_config(use_case, status, enabled);
CREATE INDEX IF NOT EXISTS idx_ai_report_model_config ON ai_report_record(model_config_code);
CREATE INDEX IF NOT EXISTS idx_ai_report_evidence_level ON ai_report_record(evidence_level, insufficient_evidence);
CREATE INDEX IF NOT EXISTS idx_ai_kb_chunk_embedding_status ON ai_kb_chunk(embedding_status);

INSERT INTO ai_model_config (
    config_code,
    config_name,
    use_case,
    provider,
    model_name,
    model_mode,
    endpoint_uri,
    prompt_template_version,
    retrieval_strategy,
    temperature,
    max_tokens,
    timeout_ms,
    enabled,
    status,
    model_config_snapshot,
    created_by,
    updated_by
)
VALUES
(
    'MOCK_YIELD_STRUCTURED',
    '试点良率日报结构化模拟配置',
    'YIELD_DAILY',
    'LOCAL',
    'mock-structured-output',
    'SIMULATED',
    '',
    'yield-daily-v2',
    'MES_SNAPSHOT',
    0.2000,
    1800,
    30000,
    1,
    'ACTIVE',
    '{"boundary":"local deterministic structured output","writeActionAllowed":false}',
    'system',
    'system'
),
(
    'MOCK_EQUIPMENT_STRUCTURED',
    '试点设备异常分析模拟配置',
    'EQUIPMENT_ANALYSIS',
    'LOCAL',
    'mock-structured-output',
    'SIMULATED',
    '',
    'equipment-analyze-v2',
    'MES_AND_RAG',
    0.2000,
    1800,
    30000,
    1,
    'ACTIVE',
    '{"boundary":"local deterministic structured output with SOP citations","writeActionAllowed":false}',
    'system',
    'system'
),
(
    'MOCK_RAG_KEYWORD',
    '试点SOP问答关键词检索配置',
    'SOP_QA',
    'LOCAL',
    'mock-rag-output',
    'SIMULATED',
    '',
    'rag-sop-qa-v2',
    'KEYWORD_FALLBACK',
    0.0000,
    1200,
    30000,
    1,
    'ACTIVE',
    '{"retrieval":"keyword fallback","vectorReady":false,"writeActionAllowed":false}',
    'system',
    'system'
),
(
    'OPENAI_COMPATIBLE_SHADOW',
    'OpenAI兼容接口影子配置占位',
    'GENERAL_SHADOW',
    'OPENAI_COMPATIBLE',
    'not-configured',
    'SHADOW',
    '',
    'shadow-v1',
    'PGVECTOR_READY',
    0.2000,
    2000,
    30000,
    0,
    'INACTIVE',
    '{"boundary":"external endpoint placeholder; enable only after security review and environment configuration","writeActionAllowed":false}',
    'system',
    'system'
)
ON CONFLICT (config_code) DO UPDATE SET
    config_name = EXCLUDED.config_name,
    use_case = EXCLUDED.use_case,
    provider = EXCLUDED.provider,
    model_name = EXCLUDED.model_name,
    model_mode = EXCLUDED.model_mode,
    endpoint_uri = EXCLUDED.endpoint_uri,
    prompt_template_version = EXCLUDED.prompt_template_version,
    retrieval_strategy = EXCLUDED.retrieval_strategy,
    temperature = EXCLUDED.temperature,
    max_tokens = EXCLUDED.max_tokens,
    timeout_ms = EXCLUDED.timeout_ms,
    enabled = EXCLUDED.enabled,
    status = EXCLUDED.status,
    model_config_snapshot = EXCLUDED.model_config_snapshot,
    updated_by = EXCLUDED.updated_by,
    updated_time = CURRENT_TIMESTAMP;

UPDATE ai_report_record
SET model_provider = COALESCE(model_provider, 'LOCAL'),
    model_mode = COALESCE(model_mode, 'SIMULATED'),
    retrieval_strategy = COALESCE(retrieval_strategy, 'NONE'),
    evidence_level = COALESCE(evidence_level, 'NONE'),
    insufficient_evidence = COALESCE(insufficient_evidence, 0)
WHERE model_provider IS NULL
   OR model_mode IS NULL
   OR retrieval_strategy IS NULL
   OR evidence_level IS NULL;

UPDATE ai_kb_chunk
SET retrieval_strategy = COALESCE(retrieval_strategy, 'KEYWORD_FALLBACK'),
    embedding_status = COALESCE(embedding_status, 'NOT_INDEXED')
WHERE retrieval_strategy IS NULL
   OR embedding_status IS NULL;
