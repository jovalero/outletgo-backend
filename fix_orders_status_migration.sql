-- =====================================================================
-- SCRIPT DE MIGRACIÓN Y CORRECCIÓN DE ESTADOS DE PEDIDOS (OUTLETGO DB)
-- =====================================================================
-- Este script sincroniza el estado de la tabla `order_stores` (slices) 
-- con el estado real de la tabla `orders` (orden global) para corregir 
-- registros inconsistentes creados en versiones anteriores.

-- 1. Sincronizar slices al estado global de la orden para pedidos Cancelados
UPDATE order_stores os
SET status = 'CANCELLED'
FROM orders o
WHERE os.order_id = o.id
  AND (o.status = 'CANCELED' OR o.status = 'CANCELLED')
  AND os.status <> 'CANCELLED';

-- 2. Sincronizar slices al estado global de la orden para pedidos Entregados
UPDATE order_stores os
SET status = 'DELIVERED'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'DELIVERED'
  AND os.status <> 'DELIVERED';

-- 3. Sincronizar slices al estado global de la orden para pedidos Pagados
UPDATE order_stores os
SET status = 'PAID'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'PAID'
  AND os.status <> 'PAID'
  AND os.status <> 'CANCELLED';

-- 4. Sincronizar slices al estado global de la orden para pedidos en Preparación
UPDATE order_stores os
SET status = 'PREPARING'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'PREPARING'
  AND os.status <> 'PREPARING'
  AND os.status <> 'CANCELLED';

-- 5. Sincronizar slices al estado global de la orden para pedidos Pendientes
UPDATE order_stores os
SET status = 'PENDING'
FROM orders o
WHERE os.order_id = o.id
  AND o.status = 'PENDING'
  AND os.status <> 'PENDING'
  AND os.status <> 'CANCELLED';

-- Verificación final de inconsistencias remaining
SELECT 
    o.id AS order_id, 
    o.status AS order_global_status, 
    os.id AS slice_id, 
    os.status AS slice_status
FROM orders o
JOIN order_stores os ON os.order_id = o.id
WHERE o.status <> os.status;
