-- BOM 生效治理：同一产品在任意时刻只能有一个 ACTIVE BOM。
-- 建索引前先收敛历史重复 ACTIVE 数据，保留更新时间/生效时间/版本/ID排序最新的一条。
WITH ranked_active_bom AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY product_code
            ORDER BY COALESCE(updated_time, effective_time, created_time, TIMESTAMP '1970-01-01') DESC,
                     bom_version DESC,
                     id DESC
        ) AS active_rank
    FROM md_bom
    WHERE deleted = 0
      AND status = 'ACTIVE'
)
UPDATE md_bom bom
SET status = 'INACTIVE',
    updated_time = CURRENT_TIMESTAMP
FROM ranked_active_bom ranked
WHERE bom.id = ranked.id
  AND ranked.active_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bom_single_active_product
ON md_bom(product_code)
WHERE deleted = 0 AND status = 'ACTIVE';
