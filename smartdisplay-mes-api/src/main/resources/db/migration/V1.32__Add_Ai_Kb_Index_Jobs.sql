CREATE TABLE IF NOT EXISTS ai_kb_index_job (
    id BIGSERIAL PRIMARY KEY,
    job_no VARCHAR(80) NOT NULL UNIQUE,
    job_type VARCHAR(40) NOT NULL DEFAULT 'MANUAL_REINDEX',
    document_no VARCHAR(80),
    retrieval_strategy VARCHAR(50) NOT NULL DEFAULT 'KEYWORD_FALLBACK',
    embedding_model VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
    target_chunk_count INT NOT NULL DEFAULT 0,
    indexed_chunk_count INT NOT NULL DEFAULT 0,
    failed_chunk_count INT NOT NULL DEFAULT 0,
    boundary_note TEXT,
    failure_reason VARCHAR(500),
    triggered_by VARCHAR(50),
    started_time TIMESTAMP,
    finished_time TIMESTAMP,
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_kb_index_job IS 'AI知识库索引任务履历';
COMMENT ON COLUMN ai_kb_index_job.job_no IS '索引任务号';
COMMENT ON COLUMN ai_kb_index_job.document_no IS '限定文档编号，空值表示全量知识库';
COMMENT ON COLUMN ai_kb_index_job.retrieval_strategy IS '检索策略：KEYWORD_FALLBACK/PGVECTOR_READY';
COMMENT ON COLUMN ai_kb_index_job.embedding_model IS '索引或向量模型标识';
COMMENT ON COLUMN ai_kb_index_job.boundary_note IS '索引边界说明，避免把占位能力误认为真实向量检索';

CREATE INDEX IF NOT EXISTS idx_ai_kb_index_job_document ON ai_kb_index_job(document_no);
CREATE INDEX IF NOT EXISTS idx_ai_kb_index_job_status ON ai_kb_index_job(status, created_time);
CREATE INDEX IF NOT EXISTS idx_ai_kb_index_job_strategy ON ai_kb_index_job(retrieval_strategy);

UPDATE ai_kb_chunk
SET embedding_status = 'NOT_INDEXED',
    retrieval_strategy = COALESCE(retrieval_strategy, 'KEYWORD_FALLBACK')
WHERE embedding_status IS NULL
   OR retrieval_strategy IS NULL;
