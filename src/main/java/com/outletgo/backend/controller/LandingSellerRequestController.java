package com.outletgo.backend.controller;

import com.outletgo.backend.entity.SellerRequest;
import com.outletgo.backend.service.SellerRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LandingSellerRequestController {

    private final SellerRequestService sellerRequestService;

    @PostMapping("/api/landing/seller-requests")
    public ResponseEntity<?> submitRequest(@RequestBody SellerRequest request) {
        log.info("=== SUBMIT SELLER REQUEST ===");
        log.info("businessName: {}", request.getBusinessName());
        log.info("cuit: {}", request.getCuit());
        log.info("contactName: {}", request.getContactName());
        log.info("email: {}", request.getEmail());
        log.info("phone: {}", request.getPhone());
        log.info("=============================");

        try {
            if (request.getBusinessName() == null || request.getBusinessName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre comercial es obligatorio."));
            }
            if (request.getCuit() == null || request.getCuit().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El CUIT es obligatorio."));
            }
            if (request.getContactName() == null || request.getContactName().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El nombre de contacto es obligatorio."));
            }
            if (request.getEmail() == null || request.getEmail().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El correo electrónico es obligatorio."));
            }
            if (request.getPhone() == null || request.getPhone().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El teléfono de contacto es obligatorio."));
            }

            SellerRequest created = sellerRequestService.createRequest(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Error creating seller request: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al procesar la solicitud: " + e.getMessage()));
        }
    }

    @GetMapping("/api/admin/seller-requests")
    public ResponseEntity<List<SellerRequest>> getRequests() {
        try {
            return ResponseEntity.ok(sellerRequestService.getAllRequests());
        } catch (Exception e) {
            log.error("Error fetching seller requests: ", e);
            return ResponseEntity.ok(new ArrayList<>());
        }
    }

    @PatchMapping("/api/admin/seller-requests/{id}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable("id") String idStr,
            @RequestBody Map<String, String> body) {
        try {
            UUID id = UUID.fromString(idStr);
            String status = body.get("status");
            if (status == null || status.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "El estado es obligatorio."));
            }
            return sellerRequestService.updateStatus(id, status)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error updating seller request status for id {}: ", idStr, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Error al actualizar estado: " + e.getMessage()));
        }
    }
}
