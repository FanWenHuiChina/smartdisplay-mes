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
VALUES (
    'LOCAL_RAG_HYBRID',
    '试点SOP问答本地混合检索配置',
    'SOP_QA',
    'LOCAL',
    'local-rag-hybrid-output',
    'SIMULATED',
    '',
    'rag-sop-qa-v3',
    'HYBRID_LOCAL',
    0.0000,
    1200,
    30000,
    1,
    'ACTIVE',
    '{"retrieval":"keyword + deterministic local vector fingerprint","vectorReady":true,"externalModelRequired":false,"writeActionAllowed":false}',
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

UPDATE ai_model_config
SET enabled = 0,
    status = 'INACTIVE',
    updated_by = 'system',
    updated_time = CURRENT_TIMESTAMP
WHERE use_case = 'SOP_QA'
  AND config_code <> 'LOCAL_RAG_HYBRID'
  AND provider = 'LOCAL';
