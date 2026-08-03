package com.outletgo.backend.service;

import com.outletgo.backend.entity.SellerRequest;
import com.outletgo.backend.repository.SellerRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SellerRequestService {

    private final SellerRequestRepository sellerRequestRepository;

    @Transactional
    public SellerRequest createRequest(SellerRequest request) {
        request.setId(null);
        if (request.getStatus() == null || request.getStatus().isBlank()) {
            request.setStatus("PENDING");
        }
        return sellerRequestRepository.save(request);
    }

    public List<SellerRequest> getAllRequests() {
        return sellerRequestRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Optional<SellerRequest> updateStatus(UUID id, String status) {
        return sellerRequestRepository.findById(id).map(req -> {
            req.setStatus(status);
            return sellerRequestRepository.save(req);
        });
    }
}
