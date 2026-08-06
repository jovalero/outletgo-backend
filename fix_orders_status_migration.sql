-- =====================================================================
-- SCRIPT DE MIGRACIÓN Y CORRECCIÓN DE ESTADOS Y SLICES DE PEDIDOS (OUTLETGO DB)
-- =====================================================================

-- 0. Eliminar restricciones CHECK obsoletas en PostgreSQL Supabase
ALTER TABLE order_stores DROP CONSTRAINT IF EXISTS order_stores_status_check;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;

-- A. Crear slices por defecto para órdenes que NO poseen ningún slice en `order_stores`
INSERT INTO order_stores (id, order_id, store_id, status, subtotal_amount, commission_rate, commission_amount, net_amount, payout_status)
SELECT 
    gen_random_uuid(),
    o.id,
    o.store_id,
    o.status,
    COALESCE(o.product_subtotal, o.total_amount, 0),
    0.10,
    COALESCE(o.product_subtotal, o.total_amount, 0) * 0.10,
    COALESCE(o.product_subtotal, o.total_amount, 0) * 0.90,
    'PENDING'
FROM orders o
WHERE NOT EXISTS (
    SELECT 1 FROM order_stores os WHERE os.order_id = o.id
);

-- B. Sincronizar slices al estado global de la orden para pedidos Cancelados
UPDATE order_stores os
SET status = 'CANCELLED'
FROM orders o
WHERE os.order_id = o.id
  AND (o.status = 'CANCELED' OR o.status = 'CANCELLED')
  AND os.status <> 'CANCELLED';

-- C. Sincronizar slices al estado global de la orden para pedidos Entregados
UPDATE order_stores os
SET status = 'DELIVERED'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'DELIVERED'
  AND os.status <> 'DELIVERED';

-- D. Sincronizar slices al estado global de la orden para pedidos Pagados
UPDATE order_stores os
SET status = 'PAID'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'PAID'
  AND os.status <> 'PAID'
  AND os.status <> 'CANCELLED';

-- E. Sincronizar slices al estado global de la orden para pedidos en Preparación
UPDATE order_stores os
SET status = 'PREPARING'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'PREPARING'
  AND os.status <> 'PREPARING'
  AND os.status <> 'CANCELLED';

-- F. Sincronizar slices al estado global de la orden para pedidos Pendientes
UPDATE order_stores os
SET status = 'PENDING'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'PENDING'
  AND os.status <> 'PENDING'
  AND os.status <> 'CANCELLED';
