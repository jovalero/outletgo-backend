package com.outletgo.backend.controller;

import com.outletgo.backend.config.JwtUtil;
import com.outletgo.backend.entity.*;
import com.outletgo.backend.repository.*;
import com.outletgo.backend.service.ImageTaggingService;
import io.jsonwebtoken.Claims;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@CrossOrigin
@Transactional
@RequestMapping("/api/seller")
@Slf4j
public class SellerController {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ImageTaggingService imageTaggingService;
    @Autowired
    private ProductVariationRepository productVariationRepository;
    @Autowired
    private ProductImageRepository productImageRepository;
    @Autowired
    private OrderStoreRepository orderStoreRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private StoreSocialRepository storeSocialRepository;
    @Autowired
    private ModerationHistoryRepository moderationHistoryRepository;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private com.outletgo.backend.service.PushNotificationService pushNotificationService;
    @Autowired
    private StoreScheduleRepository storeScheduleRepository;

    // Helper: Authenticated User
    private User getAuthenticatedUser(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.validateToken(token);
                UUID userId = UUID.fromString(claims.getSubject());
                User user = userRepository.findById(userId).orElse(null);
                if (user != null && user.getRole() == User.Role.OUTLET_OWNER) {
                    return user;
                }
            } catch (Exception e) {
                // Invalid token
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
    }

    // Helper: Authenticated Store
    private Store getAuthenticatedStore(String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        return storeRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
    }

    // Helper: Map Product Status
    private String getProductStatus(Product p) {
        if (p.getIsactive()) {
            return "ACTIVE";
        }
        List<ModerationHistory> history = moderationHistoryRepository.findByProductId(p.getId());
        boolean hasBeenDisabledByAdmin = history.stream().anyMatch(h -> "DISABLED".equalsIgnoreCase(h.getAction()));
        return hasBeenDisabledByAdmin ? "DISABLED_BY_ADMIN" : "PAUSED_BY_SELLER";
    }

    // ==========================================
    // 1. DASHBOARD ENDPOINT
    // ==========================================

    @GetMapping("/dashboard")
    public ResponseEntity<SellerDashboardResponse> getSellerDashboard(@RequestHeader("Authorization") String authHeader) {
        Store store = getAuthenticatedStore(authHeader);
        UUID storeId = store.getId();

        List<OrderStore> allSlices = orderStoreRepository.findByStoreId(storeId);
        List<Product> allProducts = productRepository.findByStoreId(storeId);
        List<Review> allReviews = reviewRepository.findByStoreId(storeId);

        // Sort slices by date desc
        allSlices.sort((s1, s2) -> s2.getOrder().getOrderDate().compareTo(s1.getOrder().getOrderDate()));

        // KPIs
        long pendingOrders = allSlices.stream()
                .filter(s -> s.getStatus() == Order.OrderStatus.PENDING || s.getStatus() == Order.OrderStatus.PREPARING)
                .count();

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        double monthlyRevenue = allSlices.stream()
                .filter(s -> s.getStatus() == Order.OrderStatus.DELIVERED && s.getOrder().getOrderDate().isAfter(thirtyDaysAgo))
                .mapToDouble(OrderStore::getSubtotalAmount)
                .sum();

        long lowStockCount = allProducts.stream()
                .filter(p -> productVariationRepository.findByProductId(p.getId()).stream().anyMatch(v -> v.getStock() <= 3))
                .count();

        long stockIssueOrders = allSlices.stream()
                .filter(s -> s.getStatus() == Order.OrderStatus.STOCK_ISSUE)
                .count();

        SellerDashboardResponse.KpisDto kpis = SellerDashboardResponse.KpisDto.builder()
                .pendingOrders((int) pendingOrders)
                .monthlyRevenue(monthlyRevenue)
                .lowStockProducts((int) lowStockCount)
                .storeRatingAvg(store.getRatingAvg())
                .storeRatingCount(store.getRatingCount())
                .stockIssueOrders((int) stockIssueOrders)
                .build();

        // Recent Orders (up to 5 actionable orders: PENDING, PREPARING, READY_FOR_PICKUP)
        List<SellerDashboardResponse.RecentOrderDto> recentOrders = allSlices.stream()
                .filter(s -> s.getStatus() == Order.OrderStatus.PENDING || s.getStatus() == Order.OrderStatus.PREPARING || s.getStatus() == Order.OrderStatus.READY_FOR_PICKUP)
                .limit(5)
                .map(s -> SellerDashboardResponse.RecentOrderDto.builder()
                        .id(s.getId().toString())
                        .orderId(s.getOrder().getId().toString())
                        .status(s.getStatus().name())
                        .subtotalArs(s.getSubtotalAmount())
                        .orderDate(s.getOrder().getOrderDate().toString())
                        .buyer(SellerDashboardResponse.BuyerDto.builder()
                                .displayName(s.getOrder().getClient().getEmail().split("@")[0])
                                .email(s.getOrder().getClient().getEmail())
                                .build())
                        .build())
                .collect(Collectors.toList());

        // Low Stock Products (up to 5 products with variations stock <= 3)
        List<SellerDashboardResponse.LowStockProductDto> lowStockProducts = allProducts.stream()
                .map(p -> {
                    List<ProductVariation> lowVars = productVariationRepository.findByProductId(p.getId()).stream()
                            .filter(v -> v.getStock() <= 3)
                            .collect(Collectors.toList());
                    if (lowVars.isEmpty()) return null;

                    List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
                    String imgUrl = imgs.isEmpty() ? null : imgs.get(0).getImageUrl();

                    return SellerDashboardResponse.LowStockProductDto.builder()
                            .id(p.getId().toString())
                            .name(p.getName())
                            .imageUrl(imgUrl)
                            .criticalVariations(lowVars.stream()
                                    .map(v -> new SellerDashboardResponse.CriticalVariationDto(v.getSize(), v.getColor(), v.getStock()))
                                    .collect(Collectors.toList()))
                            .build();
                })
                .filter(Objects::nonNull)
                .limit(5)
                .collect(Collectors.toList());

        // Recent Reviews (up to 3 recent store reviews)
        List<Review> storeReviews = allReviews.stream()
                .filter(r -> r.getProduct() == null)
                .sorted((r1, r2) -> r2.getCreatedAt().compareTo(r1.getCreatedAt()))
                .collect(Collectors.toList());

        List<SellerDashboardResponse.RecentReviewDto> recentReviews = storeReviews.stream()
                .limit(3)
                .map(r -> SellerDashboardResponse.RecentReviewDto.builder()
                        .id(r.getId().toString())
                        .authorDisplayName(r.getUser().getEmail().split("@")[0])
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .createdAt(r.getCreatedAt().toString())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(SellerDashboardResponse.builder()
                .kpis(kpis)
                .recentOrders(recentOrders)
                .lowStockProducts(lowStockProducts)
                .recentReviews(recentReviews)
                .build());
    }

    // ==========================================
    // 2. PRODUCTS ENDPOINTS
    // ==========================================

    @GetMapping("/products")
    public ResponseEntity<Page<SellerProductSummaryResponse>> getSellerProducts(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status) {

        Store store = getAuthenticatedStore(authHeader);
        List<Product> products = productRepository.findByStoreId(store.getId());

        if (name != null && !name.trim().isEmpty()) {
            String q = name.trim().toLowerCase();
            products = products.stream()
                    .filter(p -> p.getName().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        if (status != null && !status.trim().isEmpty()) {
            products = products.stream()
                    .filter(p -> getProductStatus(p).equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        // Sort by ID or name
        products.sort((p1, p2) -> p1.getName().compareToIgnoreCase(p2.getName()));

        int start = Math.min(page * size, products.size());
        int end = Math.min(start + size, products.size());
        List<Product> pagedList = products.subList(start, end);

        List<SellerProductSummaryResponse> content = pagedList.stream()
                .map(p -> {
                    List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
                    String thumb = imgs.isEmpty() ? null : imgs.get(0).getImageUrl();
                    List<ProductVariation> vars = productVariationRepository.findByProductId(p.getId());
                    int totalStock = vars.stream().mapToInt(ProductVariation::getStock).sum();
                    int lowStockCount = (int) vars.stream().filter(v -> v.getStock() <= 5).count();

                    return SellerProductSummaryResponse.builder()
                            .id(p.getId().toString())
                            .name(p.getName())
                            .thumbnailUrl(thumb)
                            .price(p.getBasePrice())
                            .totalStock(totalStock)
                            .lowStockCount(lowStockCount)
                            .status(getProductStatus(p))
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(content, PageRequest.of(page, size), products.size()));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<SellerProductDetailResponse> getSellerProductDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {

        Store store = getAuthenticatedStore(authHeader);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para ver este producto");
        }

        List<ProductImage> imgs = productImageRepository.findByProductId(product.getId());
        List<String> imageUrls = imgs.stream().map(ProductImage::getImageUrl).collect(Collectors.toList());

        List<ProductVariation> vars = productVariationRepository.findByProductId(product.getId());
        List<SellerProductDetailResponse.VariationDto> variationDtos = vars.stream()
                .map(v -> new SellerProductDetailResponse.VariationDto(v.getSize(), v.getColor(), v.getStock()))
                .collect(Collectors.toList());

        List<String> tags = product.getTags() != null ? product.getTags().stream()
                .map(Tag::getTagName)
                .collect(Collectors.toList()) : List.of();

        return ResponseEntity.ok(SellerProductDetailResponse.builder()
                .id(product.getId().toString())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId().toString() : "")
                .tags(tags)
                .basePrice(product.getBasePrice())
                .imageUrls(imageUrls)
                .status(getProductStatus(product))
                .variations(variationDtos)
                .build());
    }

    private Category resolveCategory(String categoryIdOrName) {
        if (categoryIdOrName == null || categoryIdOrName.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoría no especificada");
        }
        String cleanStr = categoryIdOrName.trim();
        try {
            UUID uuid = UUID.fromString(cleanStr);
            Optional<Category> opt = categoryRepository.findById(uuid);
            if (opt.isPresent()) {
                return opt.get();
            }
        } catch (IllegalArgumentException ignored) {
            // Not a UUID string (e.g. "ropa", "calzado"), fallback to name search
        }

        return categoryRepository.findByName(cleanStr)
                .orElseGet(() -> categoryRepository.findAll().stream()
                        .filter(c -> c.getName().equalsIgnoreCase(cleanStr))
                        .findFirst()
                        .orElseGet(() -> categoryRepository.save(Category.builder().name(cleanStr).build())));
    }

    private Set<Tag> resolveTags(List<String> manualTags, List<String> imageUrls, String productName, String description) {
        Set<Tag> tags = new HashSet<>();
        Set<String> tagNames = new LinkedHashSet<>();

        if (manualTags != null) {
            for (String t : manualTags) {
                if (t != null && !t.trim().isEmpty()) {
                    tagNames.add(t.trim().toLowerCase());
                }
            }
        }

        // Auto-extract AI tags from uploaded images & product context via Google Vision / Lens AI
        if (imageUrls != null && !imageUrls.isEmpty()) {
            try {
                Set<String> aiTags = imageTaggingService.extractTagsFromUrls(imageUrls, productName, description);
                tagNames.addAll(aiTags);
            } catch (Exception e) {
                log.warn("Fallo el etiquetado automatico de imagenes por IA: {}", e.getMessage());
            }
        }

        for (String tagName : tagNames) {
            Tag tag = tagRepository.findByTagName(tagName)
                    .orElseGet(() -> tagRepository.save(Tag.builder().tagName(tagName).build()));
            tags.add(tag);
        }

        return tags;
    }

    private Set<Tag> resolveManualTagsOnly(List<String> manualTags) {
        Set<Tag> tags = new HashSet<>();
        if (manualTags != null) {
            for (String t : manualTags) {
                if (t != null && !t.trim().isEmpty()) {
                    String cleanTag = t.trim().toLowerCase();
                    Tag tag = tagRepository.findByTagName(cleanTag)
                            .orElseGet(() -> tagRepository.save(Tag.builder().tagName(cleanTag).build()));
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    private static final Map<String, Set<String>> CATEGORY_CONFLICT_TAGS = Map.of(
        "calzado", Set.of("ropa", "remera", "remeras", "abrigos", "abrigo", "campera", "buzo", "accesorios", "gorra", "bolso"),
        "ropa", Set.of("calzado", "zapatillas", "botas", "abrigos", "campera", "buzo", "accesorios", "gorra", "bolso"),
        "abrigos", Set.of("calzado", "zapatillas", "botas", "accesorios", "gorra", "bolso"),
        "accesorios", Set.of("calzado", "zapatillas", "ropa", "remera", "abrigos", "campera", "buzo")
    );

    private Set<Tag> filterConflictingCategoryTags(Set<Tag> tags, Category category) {
        if (category == null || category.getName() == null || tags == null) {
            return tags;
        }
        String catNameLower = category.getName().toLowerCase().trim();
        Set<String> conflicts = CATEGORY_CONFLICT_TAGS.getOrDefault(catNameLower, Set.of());
        if (conflicts.isEmpty()) {
            return tags;
        }
        Set<Tag> cleanSet = new HashSet<>();
        for (Tag t : tags) {
            if (t != null && t.getTagName() != null) {
                if (!conflicts.contains(t.getTagName().toLowerCase().trim())) {
                    cleanSet.add(t);
                }
            }
        }
        return cleanSet;
    }

    @PostMapping("/products")
    public ResponseEntity<SellerProductSaveResult> createSellerProduct(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SellerProductSavePayload payload) {

        Store store = getAuthenticatedStore(authHeader);

        Category category = resolveCategory(payload.getCategoryId());
        Set<Tag> tags = resolveTags(payload.getTags(), payload.getImageUrls(), payload.getName(), payload.getDescription());
        tags = filterConflictingCategoryTags(tags, category);

        Product product = Product.builder()
                .name(payload.getName())
                .description(payload.getDescription())
                .basePrice(payload.getBasePrice())
                .category(category)
                .store(store)
                .isactive(true)
                .tags(tags)
                .build();

        Product savedProduct = productRepository.save(product);

        // Images
        if (payload.getImageUrls() != null) {
            for (String url : payload.getImageUrls()) {
                if (url != null && !url.trim().isEmpty()) {
                    productImageRepository.save(ProductImage.builder()
                            .product(savedProduct)
                            .imageUrl(url.trim())
                            .build());
                }
            }
        }

        // Variations
        if (payload.getVariations() != null) {
            for (SellerProductSavePayload.VariationDto v : payload.getVariations()) {
                productVariationRepository.save(ProductVariation.builder()
                        .product(savedProduct)
                        .size(v.getSize())
                        .color(v.getColor())
                        .stock(v.getStock())
                        .build());
            }
        }

        return ResponseEntity.ok(new SellerProductSaveResult(savedProduct.getId().toString()));
    }

    @PatchMapping("/products/{id}")
    public ResponseEntity<Void> updateSellerProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody SellerProductSavePayload payload) {

        Store store = getAuthenticatedStore(authHeader);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para actualizar este producto");
        }

        Category category = resolveCategory(payload.getCategoryId());

        // Obtener URLs de imagenes existentes para comparar si cambiaron
        List<String> existingImageUrls = productImageRepository.findByProductId(product.getId()).stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        List<String> newImageUrls = payload.getImageUrls() != null ? payload.getImageUrls() : List.of();
        boolean imagesChanged = !new HashSet<>(newImageUrls).equals(new HashSet<>(existingImageUrls));

        // Si las imagenes cambiaron, re-analizamos con IA. Si no cambiaron, respetamos la modificacion manual de tags del vendedor.
        Set<Tag> tags = imagesChanged
                ? resolveTags(payload.getTags(), newImageUrls, payload.getName(), payload.getDescription())
                : resolveManualTagsOnly(payload.getTags());
        tags = filterConflictingCategoryTags(tags, category);

        product.setName(payload.getName());
        product.setDescription(payload.getDescription());
        product.setBasePrice(payload.getBasePrice());
        product.setCategory(category);
        product.setTags(tags);
        productRepository.save(product);

        // Recreate Images using bulk SQL delete to avoid stale entity lock issues
        productImageRepository.deleteByProductId(product.getId());

        if (payload.getImageUrls() != null) {
            for (String url : payload.getImageUrls()) {
                if (url != null && !url.trim().isEmpty()) {
                    productImageRepository.save(ProductImage.builder()
                            .product(product)
                            .imageUrl(url.trim())
                            .build());
                }
            }
        }

        // Synchronize Variations in-place to preserve Foreign Key relationships with OrderItems
        List<ProductVariation> existingVars = productVariationRepository.findByProductId(product.getId());
        List<SellerProductSavePayload.VariationDto> payloadVars = payload.getVariations() != null ? payload.getVariations() : List.of();
        Set<UUID> keptVariationIds = new HashSet<>();

        for (SellerProductSavePayload.VariationDto pvDto : payloadVars) {
            if (pvDto.getSize() == null || pvDto.getColor() == null) continue;
            String sizeClean = pvDto.getSize().trim();
            String colorClean = pvDto.getColor().trim();
            int stockClean = Math.max(0, pvDto.getStock());

            Optional<ProductVariation> match = existingVars.stream()
                    .filter(ev -> ev.getSize().equalsIgnoreCase(sizeClean) && ev.getColor().equalsIgnoreCase(colorClean))
                    .findFirst();

            if (match.isPresent()) {
                ProductVariation existing = match.get();
                existing.setSize(sizeClean);
                existing.setColor(colorClean);
                existing.setStock(stockClean);
                productVariationRepository.save(existing);
                keptVariationIds.add(existing.getId());
            } else {
                ProductVariation newVar = productVariationRepository.save(ProductVariation.builder()
                        .product(product)
                        .size(sizeClean)
                        .color(colorClean)
                        .stock(stockClean)
                        .build());
                keptVariationIds.add(newVar.getId());
            }
        }

        // Clean up variations removed from payload (if not referenced by historical orders)
        for (ProductVariation oldVar : existingVars) {
            if (!keptVariationIds.contains(oldVar.getId())) {
                if (orderItemRepository.existsByVariationId(oldVar.getId())) {
                    // Safe fallback if referenced by order_items: zero out stock instead of deleting
                    oldVar.setStock(0);
                    productVariationRepository.save(oldVar);
                } else {
                    productVariationRepository.delete(oldVar);
                }
            }
        }

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/products/{id}/status")
    public ResponseEntity<Void> updateSellerProductStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {

        Store store = getAuthenticatedStore(authHeader);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para actualizar este producto");
        }

        String nextStatus = body.get("status");
        if (nextStatus == null || nextStatus.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido");
        }

        String currentStatus = getProductStatus(product);
        if ("ACTIVE".equalsIgnoreCase(nextStatus)) {
            if ("DISABLED_BY_ADMIN".equalsIgnoreCase(currentStatus)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo el admin puede reactivar este producto");
            }
            product.setIsactive(true);
        } else if ("PAUSED_BY_SELLER".equalsIgnoreCase(nextStatus)) {
            product.setIsactive(false);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado no soportado: " + nextStatus);
        }

        productRepository.save(product);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteSellerProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {

        Store store = getAuthenticatedStore(authHeader);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        if (!product.getStore().getId().equals(store.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para borrar este producto");
        }

        boolean hasOrders = orderItemRepository.existsByVariationProductId(product.getId());
        if (hasOrders) {
            // Soft delete to preserve historical order items without triggering Hibernate exceptions
            product.setIsactive(false);
            productRepository.save(product);
        } else {
            List<ProductVariation> vars = productVariationRepository.findByProductId(product.getId());
            productVariationRepository.deleteAll(vars);

            List<ProductImage> imgs = productImageRepository.findByProductId(product.getId());
            productImageRepository.deleteAll(imgs);

            productRepository.delete(product);
        }

        return ResponseEntity.ok().build();
    }

    // ==========================================
    // 3. ORDERS ENDPOINTS
    // ==========================================

    @GetMapping("/orders")
    public ResponseEntity<Page<SellerOrderStoreResponse>> getSellerOrders(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {

        Store store = getAuthenticatedStore(authHeader);
        List<OrderStore> slices = orderStoreRepository.findByStoreId(store.getId());

        if (status != null && !status.trim().isEmpty()) {
            slices = slices.stream()
                    .filter(s -> s.getStatus().name().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        // Sort by orderDate desc
        slices.sort((s1, s2) -> s2.getOrder().getOrderDate().compareTo(s1.getOrder().getOrderDate()));

        int start = Math.min(page * size, slices.size());
        int end = Math.min(start + size, slices.size());
        List<OrderStore> pagedSlices = slices.subList(start, end);

        List<SellerOrderStoreResponse> content = pagedSlices.stream()
                .map(this::mapToSellerOrderStoreResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(content, PageRequest.of(page, size), slices.size()));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<SellerOrderStoreResponse> getSellerOrderDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {

        Store store = getAuthenticatedStore(authHeader);
        OrderStore slice = orderStoreRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if (!slice.getStore().getId().equals(store.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permiso para ver este pedido");
        }

        return ResponseEntity.ok(mapToSellerOrderStoreResponse(slice));
    }

    private SellerOrderStoreResponse mapToSellerOrderStoreResponse(OrderStore slice) {
        Order order = slice.getOrder();
        List<OrderItem> items = orderItemRepository.findByOrderStoreId(slice.getId());

        List<SellerOrderStoreResponse.ItemDto> itemDtos = items.stream()
                .map(i -> SellerOrderStoreResponse.ItemDto.builder()
                        .id(i.getId().toString())
                        .productName(i.getVariation().getProduct().getName())
                        .size(i.getVariation().getSize())
                        .color(i.getVariation().getColor())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build())
                .collect(Collectors.toList());

        List<SellerOrderStoreResponse.StockIssueDto> stockIssues = new ArrayList<>();
        if (slice.getStatus() == Order.OrderStatus.STOCK_ISSUE) {
            for (OrderItem i : items) {
                if (i.getVariation().getStock() < i.getQuantity()) {
                    stockIssues.add(SellerOrderStoreResponse.StockIssueDto.builder()
                            .itemId(i.getId().toString())
                            .productName(i.getVariation().getProduct().getName())
                            .size(i.getVariation().getSize())
                            .color(i.getVariation().getColor())
                            .requestedQuantity(i.getQuantity())
                            .availableQuantity(i.getVariation().getStock())
                            .build());
                }
            }
        }

        return SellerOrderStoreResponse.builder()
                .id(slice.getId().toString())
                .orderId(order.getId().toString())
                .status(slice.getStatus().name())
                .storeName(slice.getStore().getBusinessName())
                .grossAmount(slice.getSubtotalAmount())
                .commissionRate(slice.getCommissionRate())
                .commissionAmount(slice.getCommissionAmount())
                .netAmount(slice.getNetAmount())
                .payoutStatus(slice.getPayoutStatus())
                .paidAt(slice.getPaidAt() != null ? slice.getPaidAt().toString() : null)
                .subtotalArs(slice.getSubtotalAmount())
                .shippingAddress(order.getShippingAddress())
                .orderDate(order.getOrderDate().toString())
                .buyer(SellerOrderStoreResponse.BuyerDto.builder()
                        .displayName(order.getClient().getEmail().split("@")[0])
                        .email(order.getClient().getEmail())
                        .build())
                .items(itemDtos)
                .stockIssues(stockIssues)
                .build();
    }

    // ==========================================
    // 4. STORE PROFILE ENDPOINTS
    // ==========================================

    @GetMapping("/store")
    public ResponseEntity<SellerStoreProfileResponse> getStoreProfile(@RequestHeader("Authorization") String authHeader) {
        Store store = getAuthenticatedStore(authHeader);

        List<StoreSocial> socials = storeSocialRepository.findByStoreId(store.getId());
        SellerStoreProfileResponse.SocialDto socialDto = SellerStoreProfileResponse.SocialDto.builder()
                .instagram(socials.stream().filter(s -> "instagram".equalsIgnoreCase(s.getPlatformName())).map(StoreSocial::getUrl).findFirst().orElse(null))
                .facebook(socials.stream().filter(s -> "facebook".equalsIgnoreCase(s.getPlatformName())).map(StoreSocial::getUrl).findFirst().orElse(null))
                .tiktok(socials.stream().filter(s -> "tiktok".equalsIgnoreCase(s.getPlatformName())).map(StoreSocial::getUrl).findFirst().orElse(null))
                .website(socials.stream().filter(s -> "website".equalsIgnoreCase(s.getPlatformName())).map(StoreSocial::getUrl).findFirst().orElse(null))
                .build();

        // Store Schedules from DB
        List<StoreSchedule> dbSchedules = storeScheduleRepository.findByStoreIdOrderByDayOfWeekAsc(store.getId());
        List<Map<String, Object>> businessHours = new ArrayList<>();

        if (dbSchedules.isEmpty()) {
            String[] days = {"LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO", "DOMINGO"};
            for (int i = 1; i <= 7; i++) {
                String dayName = days[i - 1];
                boolean isClosed = (i == 7);
                StoreSchedule sched = StoreSchedule.builder()
                        .store(store)
                        .dayOfWeek(i)
                        .dayName(dayName)
                        .isOpen(!isClosed)
                        .openTime(isClosed ? null : "09:00")
                        .closeTime(isClosed ? null : "18:00")
                        .build();
                storeScheduleRepository.save(sched);
                dbSchedules.add(sched);
            }
        }

        for (StoreSchedule s : dbSchedules) {
            Map<String, Object> h = new HashMap<>();
            h.put("day", s.getDayName() != null ? s.getDayName() : "DAY_" + s.getDayOfWeek());
            h.put("dayOfWeek", s.getDayOfWeek());
            h.put("isClosed", !Boolean.TRUE.equals(s.getIsOpen()));
            h.put("openTime", s.getOpenTime());
            h.put("closeTime", s.getCloseTime());
            businessHours.add(h);
        }

        Double latitude = -34.6625;
        Double longitude = -58.3672;
        String coord = store.getLocationCoord();
        if (coord != null && coord.contains(",")) {
            try {
                String[] parts = coord.split(",");
                if (parts.length >= 2) {
                    latitude = Double.parseDouble(parts[0].trim());
                    longitude = Double.parseDouble(parts[1].trim());
                }
            } catch (Exception e) {
                // Ignore parse error
            }
        }

        return ResponseEntity.ok(SellerStoreProfileResponse.builder()
                .id(store.getId().toString())
                .name(store.getBusinessName())
                .taxIdCuit(store.getCuit())
                .streetAddress(store.getAddress())
                .phone(null)
                .logoUrl(store.getHeaderImage())
                .latitude(latitude)
                .longitude(longitude)
                .social(socialDto)
                .businessHours(businessHours)
                .build());
    }

    @PatchMapping("/store")
    public ResponseEntity<SellerStoreProfileResponse> updateStoreProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SellerStoreProfileSavePayload payload) {

        Store store = getAuthenticatedStore(authHeader);

        if (payload.getName() != null) store.setBusinessName(payload.getName());
        if (payload.getTaxIdCuit() != null) store.setCuit(payload.getTaxIdCuit());
        if (payload.getStreetAddress() != null) store.setAddress(payload.getStreetAddress());
        if (payload.getLogoUrl() != null) store.setHeaderImage(payload.getLogoUrl());

        storeRepository.save(store);

        // Socials update
        List<StoreSocial> oldSocials = storeSocialRepository.findByStoreId(store.getId());
        storeSocialRepository.deleteAll(oldSocials);

        if (payload.getSocial() != null) {
            saveSocialLink(store, "instagram", payload.getSocial().getInstagram());
            saveSocialLink(store, "facebook", payload.getSocial().getFacebook());
            saveSocialLink(store, "tiktok", payload.getSocial().getTiktok());
            saveSocialLink(store, "website", payload.getSocial().getWebsite());
        }

        // Business Hours update
        if (payload.getBusinessHours() != null && !payload.getBusinessHours().isEmpty()) {
            storeScheduleRepository.deleteByStoreId(store.getId());

            String[] dayNames = {"LUNES", "MARTES", "MIÉRCOLES", "JUEVES", "VIERNES", "SÁBADO", "DOMINGO"};
            int index = 1;
            for (Map<String, Object> item : payload.getBusinessHours()) {
                String dName = item.get("day") != null ? item.get("day").toString() : dayNames[Math.min(index - 1, 6)];
                Boolean closed = Boolean.TRUE.equals(item.get("isClosed")) || Boolean.TRUE.equals(item.get("closed"));
                String oTime = item.get("openTime") != null ? item.get("openTime").toString() : null;
                String cTime = item.get("closeTime") != null ? item.get("closeTime").toString() : null;

                StoreSchedule sched = StoreSchedule.builder()
                        .store(store)
                        .dayOfWeek(index)
                        .dayName(dName)
                        .isOpen(!closed)
                        .openTime(closed ? null : oTime)
                        .closeTime(closed ? null : cTime)
                        .build();
                storeScheduleRepository.save(sched);
                index++;
            }
        }

        return getStoreProfile(authHeader);
    }

    private void saveSocialLink(Store store, String platform, String url) {
        if (url != null && !url.trim().isEmpty()) {
            storeSocialRepository.save(StoreSocial.builder()
                    .store(store)
                    .platformName(platform)
                    .url(url.trim())
                    .build());
        }
    }

    // ==========================================
    // 5. REVIEWS ENDPOINTS
    // ==========================================

    @GetMapping("/reviews/store")
    public ResponseEntity<?> getStoreReviews(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String sortCreatedAt) {

        try {
            Store store = getAuthenticatedStore(authHeader);
            List<Review> allStoreReviews = reviewRepository.findByStoreIdWithDetails(store.getId());
            List<Review> reviews = allStoreReviews.stream()
                    .filter(r -> r != null && r.getProduct() == null)
                    .filter(r -> rating == null || rating <= 0 || (r.getRating() != null && r.getRating().equals(rating)))
                    .sorted((r1, r2) -> {
                        if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                        return "ASC".equalsIgnoreCase(sortCreatedAt)
                                ? r1.getCreatedAt().compareTo(r2.getCreatedAt())
                                : r2.getCreatedAt().compareTo(r1.getCreatedAt());
                    })
                    .collect(Collectors.toList());

            if (reviews.isEmpty()) {
                reviews = allStoreReviews.stream()
                        .filter(r -> r != null)
                        .filter(r -> rating == null || rating <= 0 || (r.getRating() != null && r.getRating().equals(rating)))
                        .sorted((r1, r2) -> {
                            if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                            return "ASC".equalsIgnoreCase(sortCreatedAt)
                                    ? r1.getCreatedAt().compareTo(r2.getCreatedAt())
                                    : r2.getCreatedAt().compareTo(r1.getCreatedAt());
                        })
                        .collect(Collectors.toList());
            }

            int start = Math.min(page * size, reviews.size());
            int end = Math.min(start + size, reviews.size());
            List<Review> pagedReviews = reviews.subList(start, end);

            List<SellerReviewResponse> content = pagedReviews.stream()
                    .map(r -> {
                        String name = r.getUser() != null && r.getUser().getEmail() != null ? r.getUser().getEmail().split("@")[0] : "Cliente";
                        return SellerReviewResponse.builder()
                                .id(r.getId() != null ? r.getId().toString() : "")
                                .authorDisplayName(name)
                                .authorName(name)
                                .storeId(store.getId() != null ? store.getId().toString() : "")
                                .rating(r.getRating() != null ? r.getRating() : 0)
                                .comment(r.getComment() != null ? r.getComment() : "")
                                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "")
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new PageImpl<>(content, PageRequest.of(page, size), reviews.size()));
        } catch (ResponseStatusException rse) {
            log.info("Unauthenticated or missing seller store for reviews request: {}", rse.getMessage());
            return ResponseEntity.ok(new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0));
        } catch (Exception e) {
            log.error("Error fetching seller store reviews: ", e);
            return ResponseEntity.ok(new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0));
        }
    }

    @GetMapping("/reviews/products")
    public ResponseEntity<?> getProductReviews(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) String sortCreatedAt) {

        try {
            Store store = getAuthenticatedStore(authHeader);
            if (store == null) {
                return ResponseEntity.ok(new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0));
            }
            List<Review> reviews = reviewRepository.findByStoreIdWithDetails(store.getId()).stream()
                    .filter(r -> r != null && r.getProduct() != null)
                    .filter(r -> rating == null || rating <= 0 || (r.getRating() != null && r.getRating().equals(rating)))
                    .sorted((r1, r2) -> {
                        if (r1.getCreatedAt() == null || r2.getCreatedAt() == null) return 0;
                        return "ASC".equalsIgnoreCase(sortCreatedAt)
                                ? r1.getCreatedAt().compareTo(r2.getCreatedAt())
                                : r2.getCreatedAt().compareTo(r1.getCreatedAt());
                    })
                    .collect(Collectors.toList());

            int start = Math.min(page * size, reviews.size());
            int end = Math.min(start + size, reviews.size());
            List<Review> pagedReviews = reviews.subList(start, end);

            List<SellerProductReviewResponse> content = pagedReviews.stream()
                    .map(r -> {
                        String name = r.getUser() != null && r.getUser().getEmail() != null ? r.getUser().getEmail().split("@")[0] : "Cliente";
                        return SellerProductReviewResponse.builder()
                                .id(r.getId() != null ? r.getId().toString() : "")
                                .authorDisplayName(name)
                                .authorName(name)
                                .storeId(store.getId() != null ? store.getId().toString() : "")
                                .rating(r.getRating() != null ? r.getRating() : 0)
                                .comment(r.getComment() != null ? r.getComment() : "")
                                .createdAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : "")
                                .productId(r.getProduct() != null && r.getProduct().getId() != null ? r.getProduct().getId().toString() : "")
                                .productName(r.getProduct() != null && r.getProduct().getName() != null ? r.getProduct().getName() : "")
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(new PageImpl<>(content, PageRequest.of(page, size), reviews.size()));
        } catch (ResponseStatusException rse) {
            log.info("Unauthenticated or missing seller store for product reviews request: {}", rse.getMessage());
            return ResponseEntity.ok(new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0));
        } catch (Exception e) {
            log.error("Error fetching seller product reviews: ", e);
            return ResponseEntity.ok(new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0));
        }
    }

    // ==========================================
    // 6. CHATS ENDPOINTS
    // ==========================================

    @GetMapping("/chats")
    public ResponseEntity<?> getChats(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Store store = getAuthenticatedStore(authHeader);
        List<ChatMessage> messages = chatMessageRepository.findByStoreIdOrderBySentAtAsc(store.getId());

        Map<UUID, List<ChatMessage>> groups = messages.stream()
                .filter(m -> m.getConversationId() != null)
                .collect(Collectors.groupingBy(ChatMessage::getConversationId));

        List<Map<String, Object>> list = groups.entrySet().stream()
                .map(e -> {
                    UUID convId = e.getKey();
                    List<ChatMessage> msgs = e.getValue();
                    ChatMessage last = msgs.get(msgs.size() - 1);
                    User buyer = last.getSender().getRole() == User.Role.CLIENT ? last.getSender() : last.getReceiver();

                    long unreadCount = msgs.stream()
                            .filter(m -> m.getReceiver() != null && m.getReceiver().getId().equals(store.getUser().getId()) && !Boolean.TRUE.equals(m.getIsRead()))
                            .count();

                    Map<String, Object> map = new HashMap<>();
                    map.put("id", convId.toString());
                    map.put("buyerName", buyer.getEmail().split("@")[0]);
                    map.put("buyerEmail", buyer.getEmail());
                    map.put("lastMessagePreview", last.getContent());
                    map.put("lastMessageAt", last.getSentAt().toString());
                    map.put("unreadCount", (int) unreadCount);
                    return map;
                })
                .sorted((m1, m2) -> ((String) m2.get("lastMessageAt")).compareTo((String) m1.get("lastMessageAt")))
                .collect(Collectors.toList());

        int start = Math.min(page * size, list.size());
        int end = Math.min(start + size, list.size());
        List<Map<String, Object>> paginated = list.subList(start, end);

        return ResponseEntity.ok(new PageImpl<>(paginated, PageRequest.of(page, size), list.size()));
    }

    @Transactional
    @GetMapping("/chats/{conversationId}/messages")
    public ResponseEntity<?> getSellerChatMessages(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        Store store = getAuthenticatedStore(authHeader);
        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderBySentAtAsc(conversationId);

        // Marcar mensajes no leídos dirigidos a la tienda como LEÍDOS (isRead = true)
        List<ChatMessage> unreadToMark = messages.stream()
                .filter(m -> m.getReceiver() != null && m.getReceiver().getId().equals(store.getUser().getId()) && !Boolean.TRUE.equals(m.getIsRead()))
                .peek(m -> m.setIsRead(true))
                .collect(Collectors.toList());

        if (!unreadToMark.isEmpty()) {
            chatMessageRepository.saveAllAndFlush(unreadToMark);
        }

        List<Map<String, Object>> mapped = messages.stream()
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", m.getId().toString());
                    map.put("conversationId", m.getConversationId().toString());
                    map.put("senderRole", m.getSender().getId().equals(store.getUser().getId()) ? "SELLER" : "BUYER");
                    map.put("content", m.getContent() != null ? m.getContent() : "");
                    map.put("sentAt", m.getSentAt() != null ? m.getSentAt().toString() : LocalDateTime.now().toString());
                    return map;
                })
                .collect(Collectors.toList());

        int start = Math.min(page * size, mapped.size());
        int end = Math.min(start + size, mapped.size());
        List<Map<String, Object>> paginated = mapped.subList(start, end);

        return ResponseEntity.ok(new PageImpl<>(paginated, PageRequest.of(page, size), mapped.size()));
    }

    @PostMapping("/chats/{conversationId}/messages")
    public ResponseEntity<?> sendSellerChatMessage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID conversationId,
            @RequestBody Map<String, String> body) {

        Store store = getAuthenticatedStore(authHeader);
        String content = body.getOrDefault("content", "");
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El contenido del mensaje no puede estar vacío");
        }

        List<ChatMessage> prev = chatMessageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
        if (prev.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversación no encontrada");
        }

        // Encontrar al Comprador buscando en los mensajes de la conversación al usuario distinto de la Tienda
        UUID storeUserId = store.getUser().getId();
        User buyer = prev.stream()
                .filter(m -> m.getSender() != null && !m.getSender().getId().equals(storeUserId))
                .map(ChatMessage::getSender)
                .findFirst()
                .orElseGet(() -> prev.stream()
                        .filter(m -> m.getReceiver() != null && !m.getReceiver().getId().equals(storeUserId))
                        .map(ChatMessage::getReceiver)
                        .findFirst()
                        .orElse(null));

        String pushToken = null;
        if (buyer != null && buyer.getId() != null) {
            pushToken = userRepository.findPushTokenByUserId(buyer.getId());
        }

        System.out.println("==================================================");
        System.out.println("[SELLER-CHAT-SEND] Mensaje enviado por Tienda: " + store.getBusinessName());
        System.out.println("[SELLER-CHAT-SEND] Comprador identificado: " + (buyer != null ? buyer.getEmail() + " (ID: " + buyer.getId() + ")" : "NINGUNO"));
        System.out.println("[SELLER-CHAT-SEND] push_token directo de PostgreSQL: " + (pushToken != null ? pushToken : "NULL"));
        System.out.println("==================================================");

        ChatMessage newMsg = ChatMessage.builder()
                .conversationId(conversationId)
                .store(store)
                .sender(store.getUser())
                .receiver(buyer)
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(newMsg);

        // Enviar notificación Push al comprador (si tiene pushToken configurado)
        if (pushToken != null && !pushToken.trim().isEmpty()) {
            System.out.println("[SELLER-CHAT-SEND] Disparando PushNotificationService a token: " + pushToken);
            String title = store != null ? store.getBusinessName() : "Nuevo mensaje";
            String bodyText = saved.getContent() != null && !saved.getContent().isEmpty() 
                    ? saved.getContent() 
                    : "Te envió un mensaje";
            Map<String, Object> data = new HashMap<>();
            data.put("type", "CHAT_MESSAGE");
            data.put("conversationId", conversationId.toString());
            pushNotificationService.sendPushNotification(pushToken, title, bodyText, data);
        } else {
            System.out.println("[SELLER-CHAT-SEND] ADVERTENCIA: No se envió PushNotification porque el comprador " + (buyer != null ? buyer.getEmail() : "null") + " tiene push_token nulo o vacío en PostgreSQL.");
        }

        Map<String, Object> res = new HashMap<>();
        res.put("id", saved.getId().toString());
        res.put("conversationId", saved.getConversationId().toString());
        res.put("senderRole", "SELLER");
        res.put("content", saved.getContent());
        res.put("sentAt", saved.getSentAt().toString());

        return ResponseEntity.ok(res);
    }

    // ==========================================
    // DTO DEFINITIONS
    // ==========================================

    @Data
    @Builder
    public static class SellerDashboardResponse {
        private KpisDto kpis;
        private List<RecentOrderDto> recentOrders;
        private List<LowStockProductDto> lowStockProducts;
        private List<RecentReviewDto> recentReviews;

        @Data
        @Builder
        public static class KpisDto {
            private int pendingOrders;
            private double monthlyRevenue;
            private int lowStockProducts;
            private Double storeRatingAvg;
            private int storeRatingCount;
            private int stockIssueOrders;
        }

        @Data
        @Builder
        public static class RecentOrderDto {
            private String id;
            private String orderId;
            private String status;
            private double subtotalArs;
            private String orderDate;
            private BuyerDto buyer;
        }

        @Data
        @Builder
        public static class BuyerDto {
            private String displayName;
            private String email;
        }

        @Data
        @Builder
        public static class LowStockProductDto {
            private String id;
            private String name;
            private String imageUrl;
            private List<CriticalVariationDto> criticalVariations;
        }

        @Data
        @AllArgsConstructor
        public static class CriticalVariationDto {
            private String size;
            private String color;
            private int stock;
        }

        @Data
        @Builder
        public static class RecentReviewDto {
            private String id;
            private String authorDisplayName;
            private int rating;
            private String comment;
            private String createdAt;
        }
    }

    @Data
    @Builder
    public static class SellerProductSummaryResponse {
        private String id;
        private String name;
        private String thumbnailUrl;
        private double price;
        private int totalStock;
        private int lowStockCount;
        private String status;
    }

    @Data
    @Builder
    public static class SellerProductDetailResponse {
        private String id;
        private String name;
        private String description;
        private String categoryId;
        private List<String> tags;
        private double basePrice;
        private List<String> imageUrls;
        private String status;
        private List<VariationDto> variations;

        @Data
        @AllArgsConstructor
        public static class VariationDto {
            private String size;
            private String color;
            private int stock;
        }
    }

    @Data
    public static class SellerProductSavePayload {
        private String name;
        private String description;
        private String categoryId;
        private List<String> tags;
        private double basePrice;
        private List<String> imageUrls;
        private List<VariationDto> variations;

        @Data
        public static class VariationDto {
            private String size;
            private String color;
            private int stock;
        }
    }

    @Data
    @AllArgsConstructor
    public static class SellerProductSaveResult {
        private String id;
    }

    @Data
    @Builder
    public static class SellerOrderStoreResponse {
        private String id;
        private String orderId;
        private String status;
        private String storeName;
        private double grossAmount;
        private Double commissionRate;
        private Double commissionAmount;
        private Double netAmount;
        private String payoutStatus;
        private String paidAt;
        private double subtotalArs;
        private String shippingAddress;
        private String orderDate;
        private BuyerDto buyer;
        private List<ItemDto> items;
        private List<StockIssueDto> stockIssues;

        @Data
        @Builder
        public static class BuyerDto {
            private String displayName;
            private String email;
        }

        @Data
        @Builder
        public static class ItemDto {
            private String id;
            private String productName;
            private String size;
            private String color;
            private int quantity;
            private double unitPrice;
        }

        @Data
        @Builder
        public static class StockIssueDto {
            private String itemId;
            private String productName;
            private String size;
            private String color;
            private int requestedQuantity;
            private int availableQuantity;
        }
    }

    @Data
    @Builder
    public static class SellerStoreProfileResponse {
        private String id;
        private String name;
        private String taxIdCuit;
        private String streetAddress;
        private String phone;
        private String logoUrl;
        private double latitude;
        private double longitude;
        private SocialDto social;
        private List<Map<String, Object>> businessHours;

        @Data
        @Builder
        public static class SocialDto {
            private String instagram;
            private String facebook;
            private String tiktok;
            private String website;
        }
    }

    @Data
    public static class SellerStoreProfileSavePayload {
        private String name;
        private String taxIdCuit;
        private String streetAddress;
        private String phone;
        private String logoUrl;
        private SocialDto social;
        private List<Map<String, Object>> businessHours;

        @Data
        public static class SocialDto {
            private String instagram;
            private String facebook;
            private String tiktok;
            private String website;
        }
    }

    @Data
    @Builder
    public static class SellerReviewResponse {
        private String id;
        private String authorDisplayName;
        private String authorName;
        private String storeId;
        private int rating;
        private String comment;
        private String createdAt;
    }

    @Data
    @Builder
    public static class SellerProductReviewResponse {
        private String id;
        private String authorDisplayName;
        private String authorName;
        private String storeId;
        private int rating;
        private String comment;
        private String createdAt;
        private String productId;
        private String productName;
    }
}
