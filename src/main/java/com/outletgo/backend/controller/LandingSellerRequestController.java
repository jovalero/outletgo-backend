package com.outletgo.backend.controller;

import com.outletgo.backend.entity.SellerRequest;
import com.outletgo.backend.service.SellerRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LandingSellerRequestController {

    private final SellerRequestService sellerRequestService;

    @PostMapping("/api/landing/seller-requests")
    public ResponseEntity<SellerRequest> submitRequest(@RequestBody SellerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sellerRequestService.createRequest(request));
    }

    @GetMapping("/api/admin/seller-requests")
    public ResponseEntity<List<SellerRequest>> getRequests() {
        return ResponseEntity.ok(sellerRequestService.getAllRequests());
    }

    @PatchMapping("/api/admin/seller-requests/{id}/status")
    public ResponseEntity<SellerRequest> updateStatus(
            @PathVariable("id") UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        if (status == null || status.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        return sellerRequestService.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
