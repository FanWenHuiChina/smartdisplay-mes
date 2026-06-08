CREATE TABLE IF NOT EXISTS prod_sn (
    id BIGSERIAL PRIMARY KEY,
    sn VARCHAR(80) NOT NULL UNIQUE,
    lot_no VARCHAR(50) NOT NULL,
    order_no VARCHAR(50) NOT NULL,
    product_code VARCHAR(50) NOT NULL,
    line_code VARCHAR(50),
    sequence_no INT NOT NULL,
    grade VARCHAR(20) DEFAULT 'A',
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROCESS',
    bind_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_prod_sn ON prod_sn(sn);
CREATE INDEX IF NOT EXISTS idx_prod_sn_lot ON prod_sn(lot_no);
CREATE INDEX IF NOT EXISTS idx_prod_sn_order ON prod_sn(order_no);
CREATE INDEX IF NOT EXISTS idx_prod_sn_product ON prod_sn(product_code);
CREATE INDEX IF NOT EXISTS idx_prod_sn_status ON prod_sn(status);

INSERT INTO prod_sn (
    sn,
    lot_no,
    order_no,
    product_code,
    line_code,
    sequence_no,
    grade,
    status,
    bind_time,
    created_by
)
SELECT
    lot.lot_no || '-SN' || LPAD(series.n::TEXT, 3, '0'),
    lot.lot_no,
    lot.order_no,
    lot.product_code,
    lot.line_code,
    series.n,
    'A',
    CASE
        WHEN lot.status = 'COMPLETED' THEN 'COMPLETED'
        WHEN lot.status = 'SCRAP' THEN 'SCRAP'
        ELSE 'IN_PROCESS'
    END,
    COALESCE(lot.created_time, CURRENT_TIMESTAMP),
    COALESCE(lot.created_by, 'system')
FROM prod_lot lot
JOIN LATERAL generate_series(1, GREATEST(lot.qty, 0)) AS series(n) ON TRUE
ON CONFLICT (sn) DO NOTHING;
