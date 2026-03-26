ALTER TABLE products
    ADD COLUMN sales_count BIGINT NOT NULL DEFAULT 0 COMMENT '商品销量';

UPDATE products p
LEFT JOIN (
    SELECT oi.product_id,
           COALESCE(SUM(
               CASE
                   WHEN UPPER(o.status) IN ('PENDING_PAYMENT', 'REFUNDED') THEN 0
                   ELSE oi.quantity
               END
           ), 0) AS sales_count
    FROM order_items oi
    JOIN customer_orders o ON o.id = oi.order_id
    GROUP BY oi.product_id
) s ON s.product_id = p.id
SET p.sales_count = COALESCE(s.sales_count, 0);

CREATE INDEX idx_products_owner_sales ON products (owner_id, sales_count, id);
