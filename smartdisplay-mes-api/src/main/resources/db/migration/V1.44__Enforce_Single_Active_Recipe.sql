-- Recipe 生效治理：同一产品 + 工序 + 设备在任意时刻只能有一个 ACTIVE 版本。
-- 建索引前先收敛历史重复 ACTIVE 数据，保留更新时间/版本/ID排序最新的一条。
WITH ranked_active_recipe AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY product_code, step_code, equipment_code
            ORDER BY COALESCE(updated_time, created_time, TIMESTAMP '1970-01-01') DESC,
                     recipe_version DESC,
                     id DESC
        ) AS active_rank
    FROM md_recipe
    WHERE deleted = 0
      AND status = 'ACTIVE'
)
UPDATE md_recipe recipe
SET status = 'INACTIVE',
    updated_by = 'flyway',
    updated_time = CURRENT_TIMESTAMP
FROM ranked_active_recipe ranked
WHERE recipe.id = ranked.id
  AND ranked.active_rank > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_recipe_single_active_context
ON md_recipe(product_code, step_code, equipment_code)
WHERE deleted = 0 AND status = 'ACTIVE';
