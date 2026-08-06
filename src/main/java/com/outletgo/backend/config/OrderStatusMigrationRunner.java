package com.outletgo.backend.config;

import com.outletgo.backend.entity.Order;
import com.outletgo.backend.entity.OrderStore;
import com.outletgo.backend.repository.OrderRepository;
import com.outletgo.backend.repository.OrderStoreRepository;
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

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Iniciando migración y sincronización de estados de pedidos en la base de datos...");
        try {
            List<Order> orders = orderRepository.findAll();
            int updatedSlicesCount = 0;
            int totalOrdersCount = orders.size();

            for (Order order : orders) {
                Order.OrderStatus globalStatus = order.getStatus();
                if (globalStatus == null) {
                    continue;
                }

                List<OrderStore> slices = orderStoreRepository.findByOrderId(order.getId());
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
            log.info("Migración de estados completada exitosamente: {} pedidos examinados, {} slices sincronizados.", totalOrdersCount, updatedSlicesCount);
        } catch (Exception e) {
            log.error("Error al ejecutar la migración de estados de pedidos: ", e);
        }
    }
}
