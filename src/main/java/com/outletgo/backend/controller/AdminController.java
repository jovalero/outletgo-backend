package com.outletgo.backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outletgo.backend.config.JwtUtil;
import com.outletgo.backend.entity.*;
import com.outletgo.backend.repository.*;
import io.jsonwebtoken.Claims;
import lombok.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@Transactional
public class AdminController {


    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    @Autowired
    private ModerationHistoryRepository moderationHistoryRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private SellerWarningRepository sellerWarningRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStoreRepository orderStoreRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PickupPointRepository pickupPointRepository;

    @Autowired
    private ServiceFeeRuleRepository serviceFeeRuleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    // Helper: Admin User retrieval
    private User getAdminUser() {
        return userRepository.findByEmail("admin@outletgo.com")
                .orElseGet(() -> {
                    User admin = User.builder()
                            .email("admin@outletgo.com")
                            .password(passwordEncoder.encode("Admin123!"))
                            .role(User.Role.ADMIN)
                            .isactive(true)
                            .build();
                    return userRepository.save(admin);
                });
    }

    // Helper: Authenticated User from JWT
    private User getAuthenticatedUser(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            try {
                Claims claims = jwtUtil.validateToken(token);
                UUID userId = UUID.fromString(claims.getSubject());
                return userRepository.findById(userId).orElse(null);
            } catch (Exception e) {
                // Invalid token
            }
        }
        return null;
    }

    // ==========================================
    // 1. MODERATION (PRODUCTS) ENDPOINTS
    // ==========================================

    @GetMapping("/api/admin/products")
    public ResponseEntity<Page<AdminProductResponse>> getAdminProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID storeId) {

        Boolean isactive = null;
        if ("ACTIVE".equalsIgnoreCase(status)) {
            isactive = true;
        } else if ("DISABLED_BY_ADMIN".equalsIgnoreCase(status) || "PAUSED_BY_SELLER".equalsIgnoreCase(status)) {
            isactive = false;
        }

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Product> productPage = productRepository.searchProductsAdmin(formattedSearch, isactive, storeId, pageable);

        List<AdminProductResponse> mapped = productPage.getContent().stream()
                .map(this::mapToAdminProductResponse)
                .collect(Collectors.toList());

        // Apply strict in-memory status filter if needed since isactive maps to multiple front-end statuses
        if ("DISABLED_BY_ADMIN".equalsIgnoreCase(status)) {
            mapped = mapped.stream().filter(r -> "DISABLED_BY_ADMIN".equals(r.getStatus())).collect(Collectors.toList());
        } else if ("PAUSED_BY_SELLER".equalsIgnoreCase(status)) {
            mapped = mapped.stream().filter(r -> "PAUSED_BY_SELLER".equals(r.getStatus())).collect(Collectors.toList());
        }

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, productPage.getTotalElements()));
    }

    @GetMapping("/api/admin/products/{id}")
    public ResponseEntity<AdminProductResponse> getAdminProductDetail(@PathVariable UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        return ResponseEntity.ok(mapToAdminProductResponse(product));
    }

    @PostMapping("/api/admin/products/{id}/disable")
    public ResponseEntity<AdminProductResponse> disableProduct(
            @PathVariable UUID id,
            @RequestBody DisableProductRequest body) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        product.setIsactive(false);
        productRepository.save(product);

        ModerationHistory mh = ModerationHistory.builder()
                .product(product)
                .admin(getAdminUser())
                .action("DISABLED")
                .reason(body.getReason())
                .createdAt(LocalDateTime.now())
                .build();
        moderationHistoryRepository.save(mh);

        // Resolve all reports associated with this product
        List<Report> reports = reportRepository.findByProductId(product.getId());
        for (Report report : reports) {
            if (report.getStatus() != Report.ReportStatus.DISMISSED) {
                report.setStatus(Report.ReportStatus.RESOLVED);
                report.setResolutionType("DISABLED");
                reportRepository.save(report);
            }
        }

        return ResponseEntity.ok(mapToAdminProductResponse(product));
    }

    @PostMapping("/api/admin/products/{id}/reactivate")
    public ResponseEntity<AdminProductResponse> reactivateProduct(@PathVariable UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        product.setIsactive(true);
        productRepository.save(product);

        ModerationHistory mh = ModerationHistory.builder()
                .product(product)
                .admin(getAdminUser())
                .action("REACTIVATED")
                .reason("Reactivated by administrator")
                .createdAt(LocalDateTime.now())
                .build();
        moderationHistoryRepository.save(mh);

        // Update associated product reports that were resolved by disabling the product
        List<Report> reports = reportRepository.findByProductId(product.getId());
        for (Report report : reports) {
            if (report.getStatus() == Report.ReportStatus.RESOLVED && "DISABLED".equals(report.getResolutionType())) {
                report.setStatus(Report.ReportStatus.DISMISSED);
                report.setResolutionType("DISMISSED");
                reportRepository.save(report);
            }
        }

        return ResponseEntity.ok(mapToAdminProductResponse(product));
    }

    private AdminProductResponse mapToAdminProductResponse(Product p) {
        List<ProductImage> images = productImageRepository.findByProductId(p.getId());
        List<ProductVariation> variations = productVariationRepository.findByProductId(p.getId());
        List<ModerationHistory> history = moderationHistoryRepository.findByProductId(p.getId());

        List<AdminProductResponse.ImageDto> imageDtos = images.stream()
                .map(img -> new AdminProductResponse.ImageDto(img.getId(), img.getImageUrl()))
                .collect(Collectors.toList());

        List<AdminProductResponse.VariationDto> variationDtos = variations.stream()
                .map(v -> new AdminProductResponse.VariationDto(v.getId(), v.getSize(), v.getColor(), v.getStock()))
                .collect(Collectors.toList());

        List<AdminProductResponse.TagDto> tagDtos = p.getTags() != null ? p.getTags().stream()
                .map(t -> new AdminProductResponse.TagDto(t.getId(), t.getTagName()))
                .collect(Collectors.toList()) : List.of();

        List<AdminProductResponse.ModerationEntryDto> historyDtos = history.stream()
                .sorted((h1, h2) -> h2.getCreatedAt().compareTo(h1.getCreatedAt()))
                .map(h -> new AdminProductResponse.ModerationEntryDto(
                        h.getId(),
                        h.getAction(),
                        h.getAdmin().getEmail(),
                        h.getReason(),
                        h.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        String productStatus = "ACTIVE";
        if (!p.getIsactive()) {
            boolean hasBeenDisabledByAdmin = history.stream().anyMatch(h -> "DISABLED".equalsIgnoreCase(h.getAction()));
            productStatus = hasBeenDisabledByAdmin ? "DISABLED_BY_ADMIN" : "PAUSED_BY_SELLER";
        }

        String createdAtStr = p.getStore().getUser().getCreatedAt() != null
                ? p.getStore().getUser().getCreatedAt().toString()
                : LocalDateTime.now().toString();

        return AdminProductResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .basePrice(p.getBasePrice())
                .status(productStatus)
                .store(new AdminProductResponse.StoreDto(p.getStore().getId(), p.getStore().getBusinessName()))
                .category(new AdminProductResponse.CategoryDto(p.getCategory().getId(), p.getCategory().getName()))
                .images(imageDtos)
                .variations(variationDtos)
                .tags(tagDtos)
                .ratingAvg(p.getRatingAvg())
                .ratingCount(p.getRatingCount())
                .createdAt(createdAtStr)
                .moderationHistory(historyDtos)
                .build();
    }

    // ==========================================
    // 2. REVIEWS ENDPOINTS
    // ==========================================

    @GetMapping("/api/admin/reviews")
    public ResponseEntity<Page<AdminReviewResponse>> getAdminReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String reviewScope,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String buyerSearch,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Boolean isVisible) {

        String formattedSearch = (buyerSearch != null && !buyerSearch.trim().isEmpty()) ? "%" + buyerSearch.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Review> reviewPage = reviewRepository.searchReviewsAdmin(reviewScope, storeId, productId, isVisible, rating, formattedSearch, pageable);

        List<AdminReviewResponse> mapped = reviewPage.getContent().stream()
                .map(this::mapToAdminReviewResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, reviewPage.getTotalElements()));
    }

    @GetMapping("/api/admin/reviews/buyers/{buyerId}/history")
    public ResponseEntity<BuyerReviewHistoryResponse> getBuyerReviewHistory(@PathVariable UUID buyerId) {
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprador no encontrado"));

        List<Review> reviews = reviewRepository.findByUserId(buyerId);

        List<BuyerReviewHistoryResponse.BuyerReviewEntryResponse> reviewEntries = reviews.stream()
                .map(r -> {
                    Store store = r.getStore() != null ? r.getStore() : (r.getProduct() != null ? r.getProduct().getStore() : null);
                    UUID storeId = store != null ? store.getId() : null;
                    String storeName = store != null ? store.getBusinessName() : "";
                    UUID productId = r.getProduct() != null ? r.getProduct().getId() : null;
                    String productName = r.getProduct() != null ? r.getProduct().getName() : null;

                    return BuyerReviewHistoryResponse.BuyerReviewEntryResponse.builder()
                            .id(r.getId())
                            .rating(r.getRating())
                            .comment(r.getComment())
                            .storeId(storeId)
                            .storeName(storeName)
                            .productId(productId)
                            .productName(productName)
                            .isVisible(r.getIsVisible())
                            .createdAt(r.getCreatedAt().toString())
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(BuyerReviewHistoryResponse.builder()
                .buyerId(buyer.getId())
                .displayName(buyer.getEmail().split("@")[0])
                .email(buyer.getEmail())
                .reviews(reviewEntries)
                .build());
    }

    @PatchMapping("/api/admin/reviews/{id}/visibility")
    public ResponseEntity<AdminReviewResponse> toggleReviewVisibility(
            @PathVariable UUID id,
            @RequestBody ToggleReviewVisibilityRequest body) {

        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada"));

        review.setIsVisible(body.isVisible());
        reviewRepository.save(review);

        return ResponseEntity.ok(mapToAdminReviewResponse(review));
    }

    @DeleteMapping("/api/admin/reviews/{id}")
    public ResponseEntity<Void> deleteReview(@PathVariable UUID id) {
        if (!reviewRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reseña no encontrada");
        }
        reviewRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    private AdminReviewResponse mapToAdminReviewResponse(Review r) {
        Store store = r.getStore() != null ? r.getStore() : (r.getProduct() != null ? r.getProduct().getStore() : null);
        UUID storeId = store != null ? store.getId() : null;
        String businessName = store != null ? store.getBusinessName() : "";

        AdminReviewResponse.ProductDto productDto = null;
        if (r.getProduct() != null) {
            productDto = new AdminReviewResponse.ProductDto(r.getProduct().getId(), r.getProduct().getName());
        }

        return AdminReviewResponse.builder()
                .id(r.getId())
                .rating(r.getRating())
                .comment(r.getComment())
                .isVisible(r.getIsVisible())
                .createdAt(r.getCreatedAt().toString())
                .storeId(storeId)
                .productId(productDto != null ? productDto.getId() : null)
                .store(new AdminReviewResponse.StoreDto(storeId, businessName))
                .product(productDto)
                .buyer(new AdminReviewResponse.BuyerDto(
                        r.getUser().getId(),
                        r.getUser().getEmail().split("@")[0],
                        r.getUser().getEmail()
                ))
                .build();
    }

    // ==========================================
    // 3. BUYERS ENDPOINTS
    // ==========================================

    @GetMapping("/api/admin/buyers")
    public ResponseEntity<Page<BuyerAccountResponse>> getBuyerAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive) {

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<User> buyersPage = userRepository.searchBuyersAdmin(formattedSearch, isActive, pageable);

        List<BuyerAccountResponse> mapped = buyersPage.getContent().stream()
                .map(u -> {
                    int totalOrders = orderRepository.findByClientIdOrderByOrderDateDesc(u.getId()).size();
                    int totalReviews = reviewRepository.findByUserId(u.getId()).size();

                    return BuyerAccountResponse.builder()
                            .id(u.getId())
                            .email(u.getEmail())
                            .name(u.getEmail().split("@")[0])
                            .isActive(u.getIsactive())
                            .createdAt(u.getCreatedAt().toString())
                            .stats(new BuyerAccountResponse.Stats(totalOrders, totalReviews))
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, buyersPage.getTotalElements()));
    }

    @PatchMapping("/api/admin/buyers/{id}/email")
    public ResponseEntity<BuyerAccountResponse> updateBuyerEmail(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email inválido");
        }

        user.setEmail(email);
        userRepository.save(user);

        return ResponseEntity.ok(mapToBuyerAccountResponse(user));
    }

    @PostMapping("/api/admin/buyers/{id}/reset-password")
    public ResponseEntity<Void> resetBuyerPassword(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String temporaryPassword = body.get("temporaryPassword");
        if (temporaryPassword == null || temporaryPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña debe tener al menos 8 caracteres");
        }

        user.setPassword(passwordEncoder.encode(temporaryPassword));
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/api/admin/buyers/{id}/status")
    public ResponseEntity<BuyerAccountResponse> toggleBuyerStatus(
            @PathVariable UUID id,
            @RequestBody ToggleStatusRequest body) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        user.setIsactive(body.isActive());
        userRepository.save(user);

        return ResponseEntity.ok(mapToBuyerAccountResponse(user));
    }

    private BuyerAccountResponse mapToBuyerAccountResponse(User u) {
        int totalOrders = orderRepository.findByClientIdOrderByOrderDateDesc(u.getId()).size();
        int totalReviews = reviewRepository.findByUserId(u.getId()).size();

        return BuyerAccountResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .name(u.getEmail().split("@")[0])
                .isActive(u.getIsactive())
                .createdAt(u.getCreatedAt().toString())
                .stats(new BuyerAccountResponse.Stats(totalOrders, totalReviews))
                .build();
    }

    // ==========================================
    // 4. SELLERS ENDPOINTS
    // ==========================================

    @GetMapping("/api/admin/sellers")
    public ResponseEntity<Page<SellerAccountResponse>> getSellerAccounts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive) {

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Store> sellersPage = storeRepository.searchSellersAdmin(formattedSearch, isActive, pageable);

        List<SellerAccountResponse> mapped = sellersPage.getContent().stream()
                .map(this::mapToSellerAccountResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, sellersPage.getTotalElements()));
    }

    @PostMapping("/api/admin/sellers")
    public ResponseEntity<SellerAccountResponse> createSellerAccount(@RequestBody CreateSellerAccountRequest body) {
        if (userRepository.existsByEmail(body.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email ya está registrado");
        }

        User sellerUser = User.builder()
                .email(body.getEmail())
                .password(passwordEncoder.encode("Seller123!"))
                .role(User.Role.OUTLET_OWNER)
                .isactive(true)
                .build();
        User savedUser = userRepository.save(sellerUser);

        Store store = Store.builder()
                .user(savedUser)
                .businessName(body.getBusinessName())
                .cuit(body.getCuit())
                .address(body.getAddress())
                .description(body.getDescription())
                .ratingAvg(0.0)
                .ratingCount(0)
                .build();
        Store savedStore = storeRepository.save(store);

        return ResponseEntity.ok(mapToSellerAccountResponse(savedStore));
    }

    @PatchMapping("/api/admin/sellers/{id}")
    public ResponseEntity<SellerAccountResponse> updateSellerAccount(
            @PathVariable UUID id,
            @RequestBody UpdateSellerAccountRequest body) {

        Store store = storeRepository.findById(id).orElse(null);
        if (store == null) {
            User user = userRepository.findById(id).orElse(null);
            if (user != null) {
                store = storeRepository.findByUserId(user.getId()).orElse(null);
            }
        }

        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendedor no encontrado");
        }

        User user = store.getUser();
        if (body.getEmail() != null) {
            user.setEmail(body.getEmail());
            userRepository.save(user);
        }

        if (body.getBusinessName() != null) store.setBusinessName(body.getBusinessName());
        if (body.getCuit() != null) store.setCuit(body.getCuit());
        if (body.getAddress() != null) store.setAddress(body.getAddress());
        if (body.getDescription() != null) store.setDescription(body.getDescription());
        if (body.getHeaderImageUrl() != null) store.setHeaderImage(body.getHeaderImageUrl());
        storeRepository.save(store);

        return ResponseEntity.ok(mapToSellerAccountResponse(store));
    }

    @PatchMapping("/api/admin/sellers/{id}/status")
    public ResponseEntity<SellerAccountResponse> toggleSellerStatus(
            @PathVariable UUID id,
            @RequestBody ToggleStatusRequest body) {

        Store store = storeRepository.findById(id).orElse(null);
        if (store == null) {
            User user = userRepository.findById(id).orElse(null);
            if (user != null) {
                store = storeRepository.findByUserId(user.getId()).orElse(null);
            }
        }

        if (store == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendedor no encontrado");
        }

        User user = store.getUser();
        user.setIsactive(body.isActive());
        userRepository.save(user);

        if (!user.getIsactive()) {
            // Disabling the store: resolve all associated reports
            List<Report> reports = reportRepository.findByStoreIdOrProductStoreId(store.getId());
            for (Report report : reports) {
                if (report.getStatus() != Report.ReportStatus.DISMISSED) {
                    report.setStatus(Report.ReportStatus.RESOLVED);
                    report.setResolutionType("DISABLED");
                    reportRepository.save(report);
                }
            }
        } else {
            // Reactivating the store: reset reports that were resolved by disabling
            List<Report> reports = reportRepository.findByStoreIdOrProductStoreId(store.getId());
            for (Report report : reports) {
                if (report.getStatus() == Report.ReportStatus.RESOLVED && "DISABLED".equals(report.getResolutionType())) {
                    report.setStatus(Report.ReportStatus.DISMISSED);
                    report.setResolutionType("DISMISSED");
                    reportRepository.save(report);
                }
            }
        }

        return ResponseEntity.ok(mapToSellerAccountResponse(store));
    }

    private SellerAccountResponse mapToSellerAccountResponse(Store s) {
        User u = s.getUser();
        return SellerAccountResponse.builder()
                .id(u.getId())
                .email(u.getEmail())
                .isActive(u.getIsactive())
                .createdAt(u.getCreatedAt().toString())
                .store(SellerAccountResponse.StoreDto.builder()
                        .id(s.getId())
                        .businessName(s.getBusinessName())
                        .cuit(s.getCuit())
                        .address(s.getAddress())
                        .description(s.getDescription())
                        .headerImageUrl(s.getHeaderImage())
                        .ratingAvg(s.getRatingAvg())
                        .ratingCount(s.getRatingCount())
                        .build())
                .build();
    }

    // ==========================================
    // 5. REPORTS ENDPOINTS
    // ==========================================

    @GetMapping("/api/admin/reports/products")
    public ResponseEntity<Page<ProductReportResponse>> getProductReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String status) {

        Report.ReportStatus statusEnum = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                statusEnum = Report.ReportStatus.valueOf(status.toUpperCase());
            } catch (Exception e) {
                // Invalid status
            }
        }

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Report> reportPage = reportRepository.searchProductReportsAdmin(statusEnum, storeId, productId, formattedSearch, pageable);

        List<ProductReportResponse> mapped = reportPage.getContent().stream()
                .map(this::mapToProductReportResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, reportPage.getTotalElements()));
    }

    @GetMapping("/api/admin/reports/stores")
    public ResponseEntity<Page<StoreReportResponse>> getStoreReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String status) {

        Report.ReportStatus statusEnum = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                statusEnum = Report.ReportStatus.valueOf(status.toUpperCase());
            } catch (Exception e) {
                // Invalid status
            }
        }

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Report> reportPage = reportRepository.searchStoreReportsAdmin(statusEnum, storeId, formattedSearch, pageable);

        List<StoreReportResponse> mapped = reportPage.getContent().stream()
                .map(this::mapToStoreReportResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, reportPage.getTotalElements()));
    }

    @PostMapping("/api/admin/reports/{id}/dismiss")
    public ResponseEntity<Object> dismissReport(
            @PathVariable UUID id,
            @RequestBody DismissReportRequest body) {

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        report.setStatus(Report.ReportStatus.DISMISSED);
        report.setResolutionType("DISMISSED");
        reportRepository.save(report);

        if (report.getProduct() != null) {
            return ResponseEntity.ok(mapToProductReportResponse(report));
        } else {
            return ResponseEntity.ok(mapToStoreReportResponse(report));
        }
    }

    @PostMapping("/api/admin/reports/{id}/disable-product")
    public ResponseEntity<ProductReportResponse> disableReportedProduct(
            @PathVariable UUID id,
            @RequestBody DisableReportedProductRequest body) {

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        Product product = productRepository.findById(body.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        product.setIsactive(false);
        productRepository.save(product);

        ModerationHistory mh = ModerationHistory.builder()
                .product(product)
                .admin(getAdminUser())
                .action("DISABLED")
                .reason(body.getReason())
                .createdAt(LocalDateTime.now())
                .build();
        moderationHistoryRepository.save(mh);

        // Resolve all reports associated with this product
        List<Report> reports = reportRepository.findByProductId(product.getId());
        for (Report r : reports) {
            if (r.getStatus() != Report.ReportStatus.DISMISSED) {
                r.setStatus(Report.ReportStatus.RESOLVED);
                r.setResolutionType("DISABLED");
                reportRepository.save(r);
            }
        }

        return ResponseEntity.ok(mapToProductReportResponse(report));
    }

    @PostMapping("/api/admin/reports/{id}/disable-store")
    public ResponseEntity<StoreReportResponse> disableReportedStore(
            @PathVariable UUID id,
            @RequestBody DisableReportedStoreRequest body) {

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        Store store = storeRepository.findById(body.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        User user = store.getUser();
        user.setIsactive(false);
        userRepository.save(user);

        // Resolve all reports associated with this store and its products
        List<Report> reports = reportRepository.findByStoreIdOrProductStoreId(store.getId());
        for (Report r : reports) {
            if (r.getStatus() != Report.ReportStatus.DISMISSED) {
                r.setStatus(Report.ReportStatus.RESOLVED);
                r.setResolutionType("DISABLED");
                reportRepository.save(r);
            }
        }

        return ResponseEntity.ok(mapToStoreReportResponse(report));
    }

    @PostMapping("/api/admin/reports/{id}/warn")
    public ResponseEntity<Object> warnSellerFromReport(
            @PathVariable UUID id,
            @RequestBody WarnSellerRequest body) {

        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reporte no encontrado"));

        Store store = report.getStore() != null ? report.getStore() : (report.getProduct() != null ? report.getProduct().getStore() : null);
        if (store == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no asociada al reporte");
        }

        SellerWarning warning = SellerWarning.builder()
                .store(store)
                .admin(getAdminUser())
                .report(report)
                .message(body.getMessage())
                .createdAt(LocalDateTime.now())
                .build();
        sellerWarningRepository.save(warning);

        report.setStatus(Report.ReportStatus.RESOLVED);
        report.setResolutionType("WARNED");
        reportRepository.save(report);

        if (report.getProduct() != null) {
            return ResponseEntity.ok(mapToProductReportResponse(report));
        } else {
            return ResponseEntity.ok(mapToStoreReportResponse(report));
        }
    }

    private ProductReportResponse mapToProductReportResponse(Report r) {
        Product p = r.getProduct();
        Store store = p.getStore();
        return ProductReportResponse.builder()
                .id(r.getId())
                .reason(r.getReason())
                .status(r.getStatus().name())
                .resolutionType(r.getResolutionType())
                .createdAt(r.getCreatedAt().toString())
                .productId(p.getId())
                .storeId(store.getId())
                .reporter(new ProductReportResponse.ReporterDto(
                        r.getReporter().getId(),
                        r.getReporter().getEmail().split("@")[0],
                        r.getReporter().getEmail()
                ))
                .product(ProductReportResponse.ProductDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .currentStatus(p.getIsactive() ? "ACTIVE" : "DISABLED_BY_ADMIN")
                        .store(new ProductReportResponse.ProductDto.StoreDto(store.getId(), store.getBusinessName()))
                        .build())
                .build();
    }

    private StoreReportResponse mapToStoreReportResponse(Report r) {
        Store s = r.getStore();
        return StoreReportResponse.builder()
                .id(r.getId())
                .reason(r.getReason())
                .status(r.getStatus().name())
                .resolutionType(r.getResolutionType())
                .createdAt(r.getCreatedAt().toString())
                .storeId(s.getId())
                .reporter(new StoreReportResponse.ReporterDto(
                        r.getReporter().getId(),
                        r.getReporter().getEmail().split("@")[0],
                        r.getReporter().getEmail()
                ))
                .store(StoreReportResponse.StoreDto.builder()
                        .id(s.getId())
                        .businessName(s.getBusinessName())
                        .isActive(s.getUser().getIsactive())
                        .build())
                .build();
    }

    // ==========================================
    // 6. ORDERS ENDPOINTS
    // ==========================================

    @GetMapping("/api/admin/orders")
    public ResponseEntity<Page<AdminOrderResponse>> getAdminOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Order.OrderStatus statusEnum = null;
        if (status != null && !status.trim().isEmpty()) {
            try {
                statusEnum = Order.OrderStatus.valueOf(status.toUpperCase());
            } catch (Exception e) {
                // Invalid status
            }
        }

        LocalDateTime startDateTime = null;
        if (startDate != null && !startDate.trim().isEmpty()) {
            try {
                startDateTime = LocalDateTime.parse(startDate.contains("T") ? startDate : startDate + "T00:00:00");
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        LocalDateTime endDateTime = null;
        if (endDate != null && !endDate.trim().isEmpty()) {
            try {
                endDateTime = LocalDateTime.parse(endDate.contains("T") ? endDate : endDate + "T23:59:59");
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        UUID searchId = null;
        if (search != null && !search.trim().isEmpty()) {
            try {
                searchId = UUID.fromString(search.trim());
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size);
        Page<Order> orderPage = orderRepository.searchOrdersAdmin(searchId, formattedSearch, statusEnum, storeId, startDateTime, endDateTime, pageable);

        List<AdminOrderResponse> mapped = orderPage.getContent().stream()
                .map(this::mapToAdminOrderResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(mapped, pageable, orderPage.getTotalElements()));
    }

    @GetMapping("/api/admin/orders/{id}")
    public ResponseEntity<AdminOrderResponse> getAdminOrderDetail(@PathVariable UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada"));
        return ResponseEntity.ok(mapToAdminOrderResponse(order));
    }

    @PostMapping("/api/admin/orders/slices/{sliceId}/force-status")
    public ResponseEntity<AdminOrderStoreResponse> forceSliceStatus(
            @PathVariable UUID sliceId,
            @RequestBody ForceStatusRequest body) {

        OrderStore slice = orderStoreRepository.findById(sliceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slice de orden no encontrado"));

        try {
            slice.setStatus(Order.OrderStatus.valueOf(body.getStatus().toUpperCase()));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido");
        }

        orderStoreRepository.save(slice);
        return ResponseEntity.ok(mapToAdminOrderStoreResponse(slice));
    }

    @PostMapping("/api/admin/orders/refunds")
    public ResponseEntity<RefundResponse> refundSlice(@RequestBody RefundSliceRequest body) {
        OrderStore slice = orderStoreRepository.findById(body.getSliceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slice de orden no encontrado"));

        String mpRefundId = "REF-" + UUID.randomUUID().toString();
        slice.setMpRefundId(mpRefundId);
        slice.setRefundAmount(body.getAmount());
        slice.setStatus(Order.OrderStatus.CANCELLED);
        orderStoreRepository.save(slice);

        return ResponseEntity.ok(RefundResponse.builder()
                .success(true)
                .mpRefundId(mpRefundId)
                .refundedAmount(body.getAmount())
                .message("Reembolso procesado correctamente en Mercado Pago")
                .build());
    }

    private AdminOrderResponse mapToAdminOrderResponse(Order o) {
        List<OrderStore> slices = orderStoreRepository.findByOrderId(o.getId());

        List<AdminOrderResponse.AdminOrderStoreDto> storeDtos = slices.stream()
                .map(slice -> {
                    List<OrderItem> items = orderItemRepository.findByOrderStoreId(slice.getId());

                    List<AdminOrderResponse.AdminOrderStoreDto.LineItemDto> itemDtos = items.stream()
                            .map(item -> {
                                String imgUrl = productImageRepository.findByProductId(item.getVariation().getProduct().getId())
                                        .stream().findFirst().map(ProductImage::getImageUrl).orElse(null);
                                return AdminOrderResponse.AdminOrderStoreDto.LineItemDto.builder()
                                        .id(item.getId())
                                        .productName(item.getVariation().getProduct().getName())
                                        .size(item.getVariation().getSize())
                                        .color(item.getVariation().getColor())
                                        .quantity(item.getQuantity())
                                        .unitPrice(item.getUnitPrice())
                                        .imageUrl(imgUrl)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    AdminOrderResponse.AdminOrderStoreDto.RefundDto refund = null;
                    if (slice.getMpRefundId() != null) {
                        refund = new AdminOrderResponse.AdminOrderStoreDto.RefundDto(slice.getMpRefundId(), slice.getRefundAmount());
                    }

                    String sliceStatus = slice.getStatus().name();
                    if ("CANCELLED".equals(sliceStatus)) {
                        sliceStatus = "CANCELED";
                    }

                    return AdminOrderResponse.AdminOrderStoreDto.builder()
                            .id(slice.getId())
                            .storeStatus(sliceStatus)
                            .status(sliceStatus)
                            .storeName(slice.getStore().getBusinessName())
                            .businessName(slice.getStore().getBusinessName())
                            .grossAmount(slice.getSubtotalAmount())
                            .subtotalArs(slice.getSubtotalAmount())
                            .commissionRate(slice.getCommissionRate() != null ? slice.getCommissionRate() : 0.1)
                            .commissionAmount(slice.getCommissionAmount() != null ? slice.getCommissionAmount() : 0.0)
                            .netAmount(slice.getNetAmount() != null ? slice.getNetAmount() : slice.getSubtotalAmount())
                            .payoutStatus(slice.getPayoutStatus())
                            .paidAt(slice.getPaidAt() != null ? slice.getPaidAt().toString() : null)
                            .store(new AdminOrderResponse.AdminOrderStoreDto.StoreDto(slice.getStore().getId(), slice.getStore().getBusinessName()))
                            .items(itemDtos)
                            .refund(refund)
                            .build();
                })
                .collect(Collectors.toList());

        String globalStatus = o.getStatus().name();
        if ("CANCELLED".equals(globalStatus)) {
            globalStatus = "CANCELED";
        }

        return AdminOrderResponse.builder()
                .id(o.getId())
                .status(globalStatus)
                .createdAt(o.getOrderDate().toString())
                .orderDate(o.getOrderDate().toString())
                .productSubtotal(o.getProductSubtotal() != null ? o.getProductSubtotal() : o.getTotalAmount())
                .shippingCost(o.getShippingCost() != null ? o.getShippingCost() : 0.0)
                .serviceFee(o.getServiceFee() != null ? o.getServiceFee() : 0.0)
                .totalArs(o.getTotalAmount())
                .mpPreferenceId(o.getMpPreferenceId())
                .buyer(new AdminOrderResponse.BuyerDto(
                        o.getClient().getId(),
                        o.getClient().getEmail().split("@")[0],
                        o.getClient().getEmail()
                ))
                .stores(storeDtos)
                .build();
    }

    private AdminOrderStoreResponse mapToAdminOrderStoreResponse(OrderStore slice) {
        List<OrderItem> items = orderItemRepository.findByOrderStoreId(slice.getId());
        List<AdminOrderStoreResponse.LineItemDto> itemDtos = items.stream()
                .map(item -> {
                    String imgUrl = productImageRepository.findByProductId(item.getVariation().getProduct().getId())
                            .stream().findFirst().map(ProductImage::getImageUrl).orElse(null);
                    return AdminOrderStoreResponse.LineItemDto.builder()
                            .id(item.getId())
                            .productName(item.getVariation().getProduct().getName())
                            .size(item.getVariation().getSize())
                            .color(item.getVariation().getColor())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .imageUrl(imgUrl)
                            .build();
                })
                .collect(Collectors.toList());

        AdminOrderStoreResponse.RefundDto refund = null;
        if (slice.getMpRefundId() != null) {
            refund = new AdminOrderStoreResponse.RefundDto(slice.getMpRefundId(), slice.getRefundAmount());
        }

        String sliceStatus = slice.getStatus().name();
        if ("CANCELLED".equals(sliceStatus)) {
            sliceStatus = "CANCELED";
        }

        return AdminOrderStoreResponse.builder()
                .id(slice.getId())
                .storeId(slice.getStore().getId())
                .businessName(slice.getStore().getBusinessName())
                .storeName(slice.getStore().getBusinessName())
                .storeStatus(sliceStatus)
                .status(sliceStatus)
                .grossAmount(slice.getSubtotalAmount())
                .subtotalArs(slice.getSubtotalAmount())
                .commissionRate(slice.getCommissionRate() != null ? slice.getCommissionRate() : 0.1)
                .commissionAmount(slice.getCommissionAmount() != null ? slice.getCommissionAmount() : 0.0)
                .netAmount(slice.getNetAmount() != null ? slice.getNetAmount() : slice.getSubtotalAmount())
                .payoutStatus(slice.getPayoutStatus())
                .paidAt(slice.getPaidAt() != null ? slice.getPaidAt().toString() : null)
                .items(itemDtos)
                .refund(refund)
                .build();
    }

    // ==========================================
    // 7. SUPPORT CONVERSATIONS (ADMIN TRAY)
    // ==========================================

    @GetMapping("/api/admin/support/conversations")
    public ResponseEntity<Page<SupportConversationResponse>> getSupportConversations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        List<Store> stores = storeRepository.findAll();
        List<SupportConversationResponse> allConv = stores.stream()
                .map(store -> {
                    List<ChatMessage> messages = chatMessageRepository.findByStoreIdOrderBySentAtAsc(store.getId());

                    // Filter support messages (either sender or receiver is ADMIN)
                    List<ChatMessage> supportMsgs = messages.stream()
                            .filter(m -> m.getSender().getRole() == User.Role.ADMIN || m.getReceiver().getRole() == User.Role.ADMIN)
                            .collect(Collectors.toList());

                    SupportConversationResponse.LastMessageDto lastMsgDto = null;
                    if (!supportMsgs.isEmpty()) {
                        ChatMessage last = supportMsgs.get(supportMsgs.size() - 1);
                        lastMsgDto = SupportConversationResponse.LastMessageDto.builder()
                                .content(last.getContent())
                                .attachmentType(last.getAttachmentType())
                                .sentAt(last.getSentAt().toString())
                                .senderRole(last.getSender().getRole().name())
                                .build();
                    }

                    // Count messages from store user that admin hasn't read (we simplify to total messages sent by seller)
                    long unreadCount = supportMsgs.stream()
                            .filter(m -> m.getSender().getRole() == User.Role.OUTLET_OWNER)
                            .count();

                    return SupportConversationResponse.builder()
                            .storeId(store.getId())
                            .businessName(store.getBusinessName())
                            .sellerEmail(store.getUser().getEmail())
                            .sellerName(store.getUser().getEmail().split("@")[0])
                            .lastMessage(lastMsgDto)
                            .unreadCount((int) unreadCount)
                            .build();
                })
                .sorted((c1, c2) -> {
                    if (c1.getLastMessage() == null && c2.getLastMessage() == null) return 0;
                    if (c1.getLastMessage() == null) return 1;
                    if (c2.getLastMessage() == null) return -1;
                    return c2.getLastMessage().getSentAt().compareTo(c1.getLastMessage().getSentAt());
                })
                .collect(Collectors.toList());

        int start = Math.min(page * size, allConv.size());
        int end = Math.min((page + 1) * size, allConv.size());
        List<SupportConversationResponse> content = allConv.subList(start, end);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new PageImpl<>(content, pageable, allConv.size()));
    }

    @GetMapping("/api/admin/support/conversations/{storeId}/messages")
    public ResponseEntity<Page<SupportMessageResponse>> getAdminSupportMessages(
            @PathVariable UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<ChatMessage> messages = chatMessageRepository.findByStoreIdOrderBySentAtAsc(storeId);

        // Filter messages where one of the parties is ADMIN
        List<SupportMessageResponse> supportMsgs = messages.stream()
                .filter(m -> m.getSender().getRole() == User.Role.ADMIN || m.getReceiver().getRole() == User.Role.ADMIN)
                .map(m -> SupportMessageResponse.builder()
                        .id(m.getId())
                        .senderId(m.getSender().getId())
                        .senderRole(m.getSender().getRole().name())
                        .content(m.getContent())
                        .attachmentUrl(m.getAttachmentUrl())
                        .attachmentType(m.getAttachmentType())
                        .sentAt(m.getSentAt().toString())
                        .build())
                .collect(Collectors.toList());

        int start = Math.min(page * size, supportMsgs.size());
        int end = Math.min((page + 1) * size, supportMsgs.size());
        List<SupportMessageResponse> content = supportMsgs.subList(start, end);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new PageImpl<>(content, pageable, supportMsgs.size()));
    }

    @PostMapping("/api/admin/support/conversations/{storeId}/messages")
    public ResponseEntity<SupportMessageResponse> sendAdminSupportMessage(
            @PathVariable UUID storeId,
            @RequestBody SendSupportRequest body) {

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        ChatMessage message = ChatMessage.builder()
                .sender(getAdminUser())
                .receiver(store.getUser())
                .store(store)
                .content(body.getContent())
                .attachmentUrl(body.getAttachmentUrl())
                .attachmentType(body.getAttachmentType())
                .sentAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(message);

        return ResponseEntity.ok(SupportMessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderRole(message.getSender().getRole().name())
                .content(message.getContent())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .sentAt(message.getSentAt().toString())
                .build());
    }

    @PostMapping("/api/admin/support/conversations/{storeId}/read")
    public ResponseEntity<Void> markConversationAsRead(@PathVariable UUID storeId) {
        // Return success void response
        return ResponseEntity.ok().build();
    }

    // ==========================================
    // 8. SELLER SUPPORT ENDPOINTS
    // ==========================================

    @GetMapping("/api/support/messages")
    public ResponseEntity<Page<SupportMessageResponse>> getSellerSupportMessages(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Store store = storeRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no asociada a este usuario"));

        List<ChatMessage> messages = chatMessageRepository.findByStoreIdOrderBySentAtAsc(store.getId());

        // Filter messages where one of the parties is ADMIN
        List<SupportMessageResponse> supportMsgs = messages.stream()
                .filter(m -> m.getSender().getRole() == User.Role.ADMIN || m.getReceiver().getRole() == User.Role.ADMIN)
                .map(m -> SupportMessageResponse.builder()
                        .id(m.getId())
                        .senderId(m.getSender().getId())
                        .senderRole(m.getSender().getRole().name())
                        .content(m.getContent())
                        .attachmentUrl(m.getAttachmentUrl())
                        .attachmentType(m.getAttachmentType())
                        .sentAt(m.getSentAt().toString())
                        .build())
                .collect(Collectors.toList());

        int start = Math.min(page * size, supportMsgs.size());
        int end = Math.min((page + 1) * size, supportMsgs.size());
        List<SupportMessageResponse> content = supportMsgs.subList(start, end);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new PageImpl<>(content, pageable, supportMsgs.size()));
    }

    @PostMapping("/api/support/messages")
    public ResponseEntity<SupportMessageResponse> sendSellerSupportMessage(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody SendSupportRequest body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Store store = storeRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no asociada a este usuario"));

        ChatMessage message = ChatMessage.builder()
                .sender(user)
                .receiver(getAdminUser())
                .store(store)
                .content(body.getContent())
                .attachmentUrl(body.getAttachmentUrl())
                .attachmentType(body.getAttachmentType())
                .sentAt(LocalDateTime.now())
                .build();
        chatMessageRepository.save(message);

        return ResponseEntity.ok(SupportMessageResponse.builder()
                .id(message.getId())
                .senderId(message.getSender().getId())
                .senderRole(message.getSender().getRole().name())
                .content(message.getContent())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .sentAt(message.getSentAt().toString())
                .build());
    }

    // ==========================================
    // INNER DTO CLASSES FOR CLEAN SERIALIZATION
    // ==========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminProductResponse {
        private UUID id;
        private String name;
        private String description;
        private Double basePrice;
        private String status;
        private StoreDto store;
        private CategoryDto category;
        private List<ImageDto> images;
        private List<VariationDto> variations;
        private List<TagDto> tags;
        private Double ratingAvg;
        private Integer ratingCount;
        private String createdAt;
        private List<ModerationEntryDto> moderationHistory;

        @Data
        @AllArgsConstructor
        public static class StoreDto {
            private UUID id;
            private String businessName;
        }

        @Data
        @AllArgsConstructor
        public static class CategoryDto {
            private UUID id;
            private String name;
        }

        @Data
        @AllArgsConstructor
        public static class ImageDto {
            private UUID id;
            private String imageUrl;
        }

        @Data
        @AllArgsConstructor
        public static class VariationDto {
            private UUID id;
            private String size;
            private String color;
            private Integer stock;
        }

        @Data
        @AllArgsConstructor
        public static class TagDto {
            private UUID id;
            private String tagName;
        }

        @Data
        @AllArgsConstructor
        public static class ModerationEntryDto {
            private UUID id;
            private String action;
            private String adminEmail;
            private String reason;
            private String createdAt;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminReviewResponse {
        private UUID id;
        private Integer rating;
        private String comment;
        private Boolean isVisible;
        private String createdAt;
        private UUID storeId;
        private UUID productId;
        private StoreDto store;
        private ProductDto product;
        private BuyerDto buyer;

        @Data
        @AllArgsConstructor
        public static class StoreDto {
            private UUID id;
            private String businessName;
        }

        @Data
        @AllArgsConstructor
        public static class ProductDto {
            private UUID id;
            private String name;
        }

        @Data
        @AllArgsConstructor
        public static class BuyerDto {
            private UUID id;
            private String displayName;
            private String email;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuyerReviewHistoryResponse {
        private UUID buyerId;
        private String displayName;
        private String email;
        private List<BuyerReviewEntryResponse> reviews;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class BuyerReviewEntryResponse {
            private UUID id;
            private Integer rating;
            private String comment;
            private UUID storeId;
            private String storeName;
            private UUID productId;
            private String productName;
            private Boolean isVisible;
            private String createdAt;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BuyerAccountResponse {
        private UUID id;
        private String email;
        private String name;
        @JsonProperty("isActive")
        private boolean isActive;
        private String createdAt;
        private Stats stats;

        @Data
        @AllArgsConstructor
        public static class Stats {
            private int totalOrders;
            private int totalReviews;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SellerAccountResponse {
        private UUID id;
        private String email;
        @JsonProperty("isActive")
        private boolean isActive;
        private String createdAt;
        private StoreDto store;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StoreDto {
            private UUID id;
            private String businessName;
            private String cuit;
            private String address;
            private String description;
            private String headerImageUrl;
            private Double ratingAvg;
            private Integer ratingCount;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductReportResponse {
        private UUID id;
        private String reason;
        private String status;
        private String resolutionType;
        private String createdAt;
        private UUID productId;
        private UUID storeId;
        private ReporterDto reporter;
        private ProductDto product;

        @Data
        @AllArgsConstructor
        public static class ReporterDto {
            private UUID id;
            private String displayName;
            private String email;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ProductDto {
            private UUID id;
            private String name;
            private String currentStatus;
            private StoreDto store;

            @Data
            @AllArgsConstructor
            public static class StoreDto {
                private UUID id;
                private String businessName;
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreReportResponse {
        private UUID id;
        private String reason;
        private String status;
        private String resolutionType;
        private String createdAt;
        private UUID storeId;
        private ReporterDto reporter;
        private StoreDto store;

        @Data
        @AllArgsConstructor
        public static class ReporterDto {
            private UUID id;
            private String displayName;
            private String email;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class StoreDto {
            private UUID id;
            private String businessName;
            @JsonProperty("isActive")
            private boolean isActive;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminOrderResponse {
        private UUID id;
        private String status;
        private String createdAt;
        private String orderDate;
        private Double productSubtotal;
        private Double shippingCost;
        private Double serviceFee;
        private Double totalArs;
        private String mpPreferenceId;
        private BuyerDto buyer;
        private List<AdminOrderStoreDto> stores;

        @Data
        @AllArgsConstructor
        public static class BuyerDto {
            private UUID id;
            private String displayName;
            private String email;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class AdminOrderStoreDto {
            private UUID id;
            private String storeStatus;
            private String status;
            private String storeName;
            private String businessName;
            private Double grossAmount;
            private Double subtotalArs;
            private Double commissionRate;
            private Double commissionAmount;
            private Double netAmount;
            private String payoutStatus;
            private String paidAt;
            private StoreDto store;
            private List<LineItemDto> items;
            private RefundDto refund;

            @Data
            @AllArgsConstructor
            public static class StoreDto {
                private UUID id;
                private String businessName;
            }

            @Data
            @Builder
            @NoArgsConstructor
            @AllArgsConstructor
            public static class LineItemDto {
                private UUID id;
                private String productName;
                private String size;
                private String color;
                private int quantity;
                private Double unitPrice;
                private String imageUrl;
            }

            @Data
            @AllArgsConstructor
            public static class RefundDto {
                private String mpRefundId;
                private Double refundedAmount;
            }
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminOrderStoreResponse {
        private UUID id;
        private UUID storeId;
        private String businessName;
        private String storeName;
        private String storeStatus;
        private String status;
        private Double grossAmount;
        private Double subtotalArs;
        private Double commissionRate;
        private Double commissionAmount;
        private Double netAmount;
        private String payoutStatus;
        private String paidAt;
        private List<LineItemDto> items;
        private RefundDto refund;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class LineItemDto {
            private UUID id;
            private String productName;
            private String size;
            private String color;
            private int quantity;
            private Double unitPrice;
            private String imageUrl;
        }

        @Data
        @AllArgsConstructor
        public static class RefundDto {
            private String mpRefundId;
            private Double refundedAmount;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisableProductRequest {
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToggleReviewVisibilityRequest {
        @JsonProperty("isVisible")
        private boolean isVisible;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToggleStatusRequest {
        @JsonProperty("isActive")
        private boolean isActive;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateSellerAccountRequest {
        private String email;
        private String businessName;
        private String cuit;
        private String address;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateSellerAccountRequest {
        private String email;
        private String businessName;
        private String cuit;
        private String address;
        private String description;
        private String headerImageUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DismissReportRequest {
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisableReportedProductRequest {
        private UUID productId;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisableReportedStoreRequest {
        private UUID storeId;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WarnSellerRequest {
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForceStatusRequest {
        private String status;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundSliceRequest {
        private UUID sliceId;
        private Double amount;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundResponse {
        private boolean success;
        private String mpRefundId;
        private Double refundedAmount;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportConversationResponse {
        private UUID storeId;
        private String businessName;
        private String sellerEmail;
        private String sellerName;
        private LastMessageDto lastMessage;
        private int unreadCount;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class LastMessageDto {
            private String content;
            private String attachmentType;
            private String sentAt;
            private String senderRole;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SupportMessageResponse {
        private UUID id;
        private UUID senderId;
        private String senderRole;
        private String content;
        private String attachmentUrl;
        private String attachmentType;
        private String sentAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SendSupportRequest {
        private String content;
        private String attachmentUrl;
        private String attachmentType;
    }

    // ==========================================
    // 9. PICKUP POINTS (SHIPPING)
    // ==========================================

    @GetMapping("/api/admin/shipping/pickup-points")
    public ResponseEntity<Page<PickupPoint>> getPickupPoints(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive) {

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<PickupPoint> result = pickupPointRepository.searchPickupPointsAdmin(isActive, formattedSearch, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/shipping/pickup-points")
    public ResponseEntity<PickupPoint> createPickupPoint(@RequestBody CreatePickupPointRequest body) {
        PickupPoint pp = PickupPoint.builder()
                .name(body.getName())
                .address(body.getAddress())
                .neighborhood(body.getNeighborhood())
                .city(body.getCity())
                .lat(body.getLat())
                .lng(body.getLng())
                .businessHours(body.getBusinessHours())
                .isActive(true)
                .build();
        pickupPointRepository.save(pp);
        return ResponseEntity.ok(pp);
    }

    @PatchMapping("/api/admin/shipping/pickup-points/{id}")
    public ResponseEntity<PickupPoint> updatePickupPoint(
            @PathVariable UUID id,
            @RequestBody CreatePickupPointRequest body) {
        PickupPoint pp = pickupPointRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Punto de retiro no encontrado"));
        pp.setName(body.getName());
        pp.setAddress(body.getAddress());
        pp.setNeighborhood(body.getNeighborhood());
        pp.setCity(body.getCity());
        pp.setLat(body.getLat());
        pp.setLng(body.getLng());
        pp.setBusinessHours(body.getBusinessHours());
        pickupPointRepository.save(pp);
        return ResponseEntity.ok(pp);
    }

    @PatchMapping("/api/admin/shipping/pickup-points/{id}/status")
    public ResponseEntity<PickupPoint> togglePickupPointStatus(
            @PathVariable UUID id,
            @RequestBody TogglePickupPointStatusRequest body) {
        PickupPoint pp = pickupPointRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Punto de retiro no encontrado"));
        pp.setIsActive(body.getIsActive());
        pickupPointRepository.save(pp);
        return ResponseEntity.ok(pp);
    }

    // ==========================================
    // 10. SERVICE FEE RULES
    // ==========================================

    @GetMapping("/api/admin/service-fee-rules")
    public ResponseEntity<Page<ServiceFeeRule>> getServiceFeeRules(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean isActive) {

        String formattedSearch = (search != null && !search.trim().isEmpty()) ? "%" + search.trim().toLowerCase() + "%" : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "priority").and(Sort.by(Sort.Direction.ASC, "name")));
        Page<ServiceFeeRule> result = serviceFeeRuleRepository.searchServiceFeeRulesAdmin(isActive, formattedSearch, pageable);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/admin/service-fee-rules")
    public ResponseEntity<ServiceFeeRule> createServiceFeeRule(@RequestBody CreateServiceFeeRuleRequest body) {
        ServiceFeeRule rule = ServiceFeeRule.builder()
                .name(body.getName())
                .feeType(body.getFeeType())
                .feeValue(body.getFeeValue())
                .feeTarget(body.getFeeTarget())
                .shippingMethod(body.getShippingMethod())
                .minOrderAmount(body.getMinOrderAmount() != null ? body.getMinOrderAmount() : 0.0)
                .isActive(body.getIsActive() != null ? body.getIsActive() : true)
                .validFrom(body.getValidFrom() != null ? LocalDateTime.parse(body.getValidFrom().replace("Z", "")) : null)
                .validUntil(body.getValidUntil() != null ? LocalDateTime.parse(body.getValidUntil().replace("Z", "")) : null)
                .priority(body.getPriority() != null ? body.getPriority() : 0)
                .build();
        serviceFeeRuleRepository.save(rule);
        return ResponseEntity.ok(rule);
    }

    @PatchMapping("/api/admin/service-fee-rules/{id}")
    public ResponseEntity<ServiceFeeRule> updateServiceFeeRule(
            @PathVariable UUID id,
            @RequestBody CreateServiceFeeRuleRequest body) {
        ServiceFeeRule rule = serviceFeeRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla de tarifa no encontrada"));
        rule.setName(body.getName());
        rule.setFeeType(body.getFeeType());
        rule.setFeeValue(body.getFeeValue());
        rule.setFeeTarget(body.getFeeTarget());
        rule.setShippingMethod(body.getShippingMethod());
        rule.setMinOrderAmount(body.getMinOrderAmount() != null ? body.getMinOrderAmount() : 0.0);
        rule.setIsActive(body.getIsActive() != null ? body.getIsActive() : true);
        rule.setValidFrom(body.getValidFrom() != null ? LocalDateTime.parse(body.getValidFrom().replace("Z", "")) : null);
        rule.setValidUntil(body.getValidUntil() != null ? LocalDateTime.parse(body.getValidUntil().replace("Z", "")) : null);
        rule.setPriority(body.getPriority() != null ? body.getPriority() : 0);
        serviceFeeRuleRepository.save(rule);
        return ResponseEntity.ok(rule);
    }

    @PatchMapping("/api/admin/service-fee-rules/{id}/status")
    public ResponseEntity<ServiceFeeRule> toggleServiceFeeRuleStatus(
            @PathVariable UUID id,
            @RequestBody ToggleServiceFeeRuleStatusRequest body) {
        ServiceFeeRule rule = serviceFeeRuleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Regla de tarifa no encontrada"));
        rule.setIsActive(body.getIsActive());
        serviceFeeRuleRepository.save(rule);
        return ResponseEntity.ok(rule);
    }

    // ==========================================
    // 11. ADMIN DASHBOARD STATS
    // ==========================================

    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<AdminDashboardResponse> getAdminDashboardStats() {
        List<Order> allOrders = orderRepository.findAll();
        
        double totalGmv = 0.0;
        double totalServiceFees = 0.0;
        double totalCommissions = 0.0;
        long totalOrdersCount = allOrders.size();
        
        for (Order o : allOrders) {
            String status = o.getStatus().name();
            if (!"CANCELED".equals(status) && !"CANCELLED".equals(status) && !"PENDING".equals(status)) {
                totalGmv += o.getTotalAmount() != null ? o.getTotalAmount() : 0.0;
                totalServiceFees += o.getServiceFee() != null ? o.getServiceFee() : 0.0;
                
                List<OrderStore> slices = orderStoreRepository.findByOrderId(o.getId());
                for (OrderStore slice : slices) {
                    if (slice.getStatus() != Order.OrderStatus.CANCELED && slice.getStatus() != Order.OrderStatus.CANCELLED) {
                        totalCommissions += slice.getCommissionAmount() != null ? slice.getCommissionAmount() : 0.0;
                    }
                }
            }
        }
        
        long activeStoresCount = storeRepository.findAll().stream()
                .filter(s -> s.getUser() != null && s.getUser().getIsactive())
                .count();
                
        long pendingReportsCount = reportRepository.findAll().stream()
                .filter(r -> r.getStatus() == Report.ReportStatus.PENDING)
                .count();

        long pendingRefundsCount = orderStoreRepository.findAll().stream()
                .filter(slice -> (slice.getStatus() == Order.OrderStatus.CANCELED || slice.getStatus() == Order.OrderStatus.CANCELLED) && slice.getMpRefundId() == null)
                .count();
                
        long stockIssuesCount = orderStoreRepository.findAll().stream()
                .filter(slice -> slice.getStatus() == Order.OrderStatus.STOCK_ISSUE)
                .count();
                
        List<Store> stores = storeRepository.findAll();
        long unreadSupportConversationsCount = stores.stream()
                .filter(store -> {
                    List<ChatMessage> messages = chatMessageRepository.findByStoreIdOrderBySentAtAsc(store.getId());
                    return messages.stream()
                            .anyMatch(m -> (m.getSender().getRole() == User.Role.ADMIN || m.getReceiver().getRole() == User.Role.ADMIN) 
                                    && m.getSender().getRole() == User.Role.OUTLET_OWNER);
                })
                .count();

        // Get 5 most recent orders
        Pageable recentPageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "orderDate"));
        Page<Order> recentOrdersPage = orderRepository.findAll(recentPageable);
        List<AdminOrderResponse> recentOrders = recentOrdersPage.getContent().stream()
                .map(this::mapToAdminOrderResponse)
                .collect(Collectors.toList());

        AdminDashboardResponse response = AdminDashboardResponse.builder()
                .totalGmv(totalGmv)
                .totalCommissions(totalCommissions)
                .totalServiceFees(totalServiceFees)
                .totalOrdersCount(totalOrdersCount)
                .activeStoresCount(activeStoresCount)
                .pendingReportsCount(pendingReportsCount)
                .pendingRefundsCount(pendingRefundsCount)
                .stockIssuesCount(stockIssuesCount)
                .unreadSupportConversationsCount(unreadSupportConversationsCount)
                .recentOrders(recentOrders)
                .build();

        return ResponseEntity.ok(response);
    }

    // ==========================================
    // REQUEST / RESPONSE DTO CLASSES
    // ==========================================

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePickupPointRequest {
        private String name;
        private String address;
        private String neighborhood;
        private String city;
        private Double lat;
        private Double lng;
        private String businessHours;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TogglePickupPointStatusRequest {
        @JsonProperty("isActive")
        private boolean isActive;
        private String reason;

        public boolean getIsActive() {
            return isActive;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateServiceFeeRuleRequest {
        private String name;
        private String feeType;
        private Double feeValue;
        private String feeTarget;
        private String shippingMethod;
        private Double minOrderAmount;
        @JsonProperty("isActive")
        private Boolean isActive;
        private String validFrom;
        private String validUntil;
        private Integer priority;

        public Boolean getIsActive() {
            return isActive;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToggleServiceFeeRuleStatusRequest {
        @JsonProperty("isActive")
        private boolean isActive;

        public boolean getIsActive() {
            return isActive;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminDashboardResponse {
        private Double totalGmv;
        private Double totalCommissions;
        private Double totalServiceFees;
        private long totalOrdersCount;
        private long activeStoresCount;
        private long pendingReportsCount;
        private long pendingRefundsCount;
        private long stockIssuesCount;
        private long unreadSupportConversationsCount;
        private List<AdminOrderResponse> recentOrders;
    }
}
