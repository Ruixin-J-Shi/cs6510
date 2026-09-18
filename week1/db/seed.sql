-- Seed 2000 catalog items at 10 000 stock each.
-- Price formula gives a deterministic spread from $0.50 to $49.95.
-- SKU format matches the mockserver: SKU-NNNNNN
INSERT INTO inventory (sku, name, price, stock)
SELECT
    'SKU-' || LPAD(i::text, 6, '0')                       AS sku,
    'Product ' || LPAD(i::text, 6, '0')                    AS name,
    ROUND((0.50 + ((i - 1) % 100) * 0.495)::numeric, 2)   AS price,
    10000                                                   AS stock
FROM generate_series(1, 2000) AS i
ON CONFLICT (sku) DO NOTHING;
