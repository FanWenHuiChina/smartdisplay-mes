ALTER TABLE prod_lot
    ADD COLUMN IF NOT EXISTS line_code VARCHAR(50);

UPDATE prod_lot lot
SET line_code = orders.line_code
FROM prod_order orders
WHERE lot.order_no = orders.order_no
  AND (lot.line_code IS NULL OR lot.line_code = '');

UPDATE prod_lot
SET line_code = 'LINE_01'
WHERE line_code IS NULL OR line_code = '';

CREATE INDEX IF NOT EXISTS idx_lot_line_code ON prod_lot(line_code);
