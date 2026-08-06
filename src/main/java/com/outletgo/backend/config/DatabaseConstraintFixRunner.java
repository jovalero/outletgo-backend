package com.outletgo.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DatabaseConstraintFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("Eliminando restricciones CHECK obsoletas en PostgreSQL para permitir nuevos estados de pedidos...");
        try {
            jdbcTemplate.execute("ALTER TABLE order_stores DROP CONSTRAINT IF EXISTS order_stores_status_check");
            jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
            log.info("Restricciones order_stores_status_check y orders_status_check removidas exitosamente.");
        } catch (Exception e) {
            log.warn("No se pudo eliminar la restricción CHECK de PostgreSQL (posible falta de permisos o inexistencia): {}", e.getMessage());
        }
    }
}
