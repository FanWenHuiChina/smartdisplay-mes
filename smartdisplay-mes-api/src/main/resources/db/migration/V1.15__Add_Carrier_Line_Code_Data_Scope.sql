ALTER TABLE material_carrier
    ADD COLUMN IF NOT EXISTS line_code VARCHAR(50);

UPDATE material_carrier carrier
SET line_code = lot.line_code
FROM prod_lot lot
WHERE carrier.lot_no = lot.lot_no
  AND (carrier.line_code IS NULL OR carrier.line_code = '');

UPDATE material_carrier
SET line_code = location
WHERE (line_code IS NULL OR line_code = '')
  AND location LIKE 'LINE\_%' ESCAPE '\';

UPDATE material_carrier
SET line_code = 'LINE_01'
WHERE line_code IS NULL OR line_code = '';

CREATE INDEX IF NOT EXISTS idx_material_carrier_line_code ON material_carrier(line_code);
