package com.outletgo.backend.config;

import com.outletgo.backend.entity.Order;
import com.outletgo.backend.entity.OrderStore;
import com.outletgo.backend.entity.SystemSetting;
import com.outletgo.backend.repository.OrderRepository;
import com.outletgo.backend.repository.OrderStoreRepository;
import com.outletgo.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStatusMigrationRunner implements CommandLineRunner {

    private final OrderRepository orderRepository;
    private final OrderStoreRepository orderStoreRepository;
    private final SystemSettingRepository systemSettingRepository;

    private static final String MIGRATION_KEY = "orders_v1_slices_migration_done";

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (systemSettingRepository.existsById(MIGRATION_KEY)) {
            log.info("Migración de pedidos ya fue ejecutada anteriormente. Omitiendo ejecución.");
            return;
        }

        log.info("Iniciando migración ÚNICA de saneamiento de pedidos y creación de slices faltantes...");
        try {
            List<Order> orders = orderRepository.findAll();
            int createdSlicesCount = 0;
            int updatedSlicesCount = 0;

            for (Order order : orders) {
                Order.OrderStatus globalStatus = order.getStatus() != null ? order.getStatus() : Order.OrderStatus.PENDING;
                List<OrderStore> slices = orderStoreRepository.findByOrderId(order.getId());

                // Si la orden no posee slices por tienda (versión antigua o de prueba), crear slice por defecto de la tienda
                if (slices.isEmpty() && order.getStore() != null) {
                    double subtotal = order.getProductSubtotal() != null ? order.getProductSubtotal() : (order.getTotalAmount() != null ? order.getTotalAmount() : 0.0);
                    OrderStore newSlice = OrderStore.builder()
                            .order(order)
                            .store(order.getStore())
                            .status(globalStatus)
                            .subtotalAmount(subtotal)
                            .commissionRate(0.10)
                            .commissionAmount(subtotal * 0.10)
                            .netAmount(subtotal * 0.90)
                            .payoutStatus("PENDING")
                            .build();
                    OrderStore saved = orderStoreRepository.save(newSlice);
                    slices = List.of(saved);
                    createdSlicesCount++;
                }

                // Sincronizar estado de los slices con el estado global de la orden
                for (OrderStore slice : slices) {
                    boolean isAlreadyCancelled = slice.getStatus() == Order.OrderStatus.CANCELED || slice.getStatus() == Order.OrderStatus.CANCELLED;
                    boolean isGlobalCancelled = globalStatus == Order.OrderStatus.CANCELED || globalStatus == Order.OrderStatus.CANCELLED;

                    if (isGlobalCancelled) {
                        if (slice.getStatus() != Order.OrderStatus.CANCELLED) {
                            slice.setStatus(Order.OrderStatus.CANCELLED);
                            orderStoreRepository.save(slice);
                            updatedSlicesCount++;
                        }
                    } else if (!isAlreadyCancelled && slice.getStatus() != globalStatus) {
                        slice.setStatus(globalStatus);
                        orderStoreRepository.save(slice);
                        updatedSlicesCount++;
                    }
                }
            }

            // Registrar bandera en la BD para garantizar que la migración ejecute UNA SOLA VEZ y nunca más
            systemSettingRepository.save(SystemSetting.builder()
                    .settingKey(MIGRATION_KEY)
                    .settingValue("true")
                    .build());

            log.info("Migración única finalizada con éxito: {} órdenes procesadas, {} slices creados, {} slices actualizados.",
                    orders.size(), createdSlicesCount, updatedSlicesCount);
        } catch (Exception e) {
            log.error("Error al ejecutar la migración única de pedidos: ", e);
        }
    }
}
