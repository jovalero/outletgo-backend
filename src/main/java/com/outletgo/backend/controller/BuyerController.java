package com.outletgo.backend.controller;

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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/buyer")
@CrossOrigin
public class BuyerController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderStoreRepository orderStoreRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductFavoriteRepository productFavoriteRepository;

    @Autowired
    private StoreFavoriteRepository storeFavoriteRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PickupPointRepository pickupPointRepository;

    @Autowired
    private ServiceFeeRuleRepository serviceFeeRuleRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private StoreSocialRepository storeSocialRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

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

    // Helper: Haversine distance formula (in km)
    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0; // in km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    // ==========================================
    // 1. CATALOG & PRODUCTS
    // ==========================================

    @GetMapping("/catalog/categories")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @GetMapping("/catalog/products")
    public ResponseEntity<?> getCatalogProducts(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID storeId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String sizeFilter,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        List<Product> list = productRepository.findByIsactiveTrue();
        Stream<Product> stream = list.stream();

        if (categoryId != null) {
            stream = stream.filter(p -> p.getCategory().getId().equals(categoryId));
        }
        if (storeId != null) {
            stream = stream.filter(p -> p.getStore().getId().equals(storeId));
        }
        if (name != null && !name.trim().isEmpty()) {
            String searchLower = name.trim().toLowerCase();
            stream = stream.filter(p -> p.getName().toLowerCase().contains(searchLower));
        }
        if (minPrice != null) {
            stream = stream.filter(p -> p.getBasePrice() >= minPrice);
        }
        if (maxPrice != null) {
            stream = stream.filter(p -> p.getBasePrice() <= maxPrice);
        }

        List<Product> filtered = stream.collect(Collectors.toList());

        List<CatalogProductDto> dtos = filtered.stream()
                .map(p -> {
                    List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
                    String thumb = imgs.isEmpty() ? null : imgs.get(0).getImageUrl();

                    Double dist = null;
                    if (latitude != null && longitude != null && p.getStore().getLocationCoord() != null) {
                        try {
                            String[] coords = p.getStore().getLocationCoord().split(",");
                            double storeLat = Double.parseDouble(coords[0].trim());
                            double storeLng = Double.parseDouble(coords[1].trim());
                            dist = calculateDistanceKm(latitude, longitude, storeLat, storeLng);
                        } catch (Exception e) {}
                    }

                    return CatalogProductDto.builder()
                            .id(p.getId())
                            .name(p.getName())
                            .thumbnailUrl(thumb)
                            .price(p.getBasePrice())
                            .storeId(p.getStore().getId())
                            .storeName(p.getStore().getBusinessName())
                            .ratingAvg(p.getRatingAvg())
                            .ratingCount(p.getRatingCount())
                            .distanceKm(dist)
                            .build();
                })
                .collect(Collectors.toList());

        if (radiusKm != null) {
            dtos = dtos.stream()
                    .filter(dto -> dto.getDistanceKm() != null && dto.getDistanceKm() <= radiusKm)
                    .collect(Collectors.toList());
        }

        int start = Math.min(page * size, dtos.size());
        int end = Math.min((page + 1) * size, dtos.size());
        List<CatalogProductDto> paginated = dtos.subList(start, end);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new PageImpl<>(paginated, pageable, dtos.size()));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<?> getProductDetail(@PathVariable UUID productId) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        List<ProductImage> images = productImageRepository.findByProductId(productId);
        List<String> imageUrls = images.stream().map(ProductImage::getImageUrl).collect(Collectors.toList());

        List<ProductVariation> variations = productVariationRepository.findByProductId(productId);
        List<VariationDto> varDtos = variations.stream()
                .map(v -> VariationDto.builder()
                        .id(v.getId())
                        .size(v.getSize())
                        .color(v.getColor())
                        .stock(v.getStock())
                        .price(p.getBasePrice())
                        .build())
                .collect(Collectors.toList());

        List<Review> reviews = reviewRepository.findByProductId(productId);
        List<ReviewDto> revDtos = reviews.stream()
                .filter(Review::getIsVisible)
                .map(r -> ReviewDto.builder()
                        .id(r.getId())
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .authorName(r.getUser().getEmail().split("@")[0])
                        .createdAt(r.getCreatedAt().toString())
                        .isVisible(r.getIsVisible())
                        .imageUrls(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());

        ProductDetailDto dto = ProductDetailDto.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .imageUrls(imageUrls)
                .thumbnailUrl(imageUrls.isEmpty() ? null : imageUrls.get(0))
                .price(p.getBasePrice())
                .storeId(p.getStore().getId())
                .storeName(p.getStore().getBusinessName())
                .ratingAvg(p.getRatingAvg())
                .ratingCount(p.getRatingCount())
                .variations(varDtos)
                .reviews(revDtos)
                .build();

        return ResponseEntity.ok(dto);
    }

    // ==========================================
    // 2. STORES & MAPS
    // ==========================================

    @GetMapping("/stores/{storeId}")
    public ResponseEntity<?> getStoreProfile(
            @PathVariable UUID storeId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {

        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        List<Review> reviews = reviewRepository.findByStoreId(storeId);
        List<ReviewDto> revDtos = reviews.stream()
                .filter(Review::getIsVisible)
                .map(r -> ReviewDto.builder()
                        .id(r.getId())
                        .rating(r.getRating())
                        .comment(r.getComment())
                        .authorName(r.getUser().getEmail().split("@")[0])
                        .createdAt(r.getCreatedAt().toString())
                        .isVisible(r.getIsVisible())
                        .imageUrls(new ArrayList<>())
                        .build())
                .collect(Collectors.toList());

        List<StoreScheduleDto> schedule = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            schedule.add(StoreScheduleDto.builder()
                    .dayOfWeek(i)
                    .isOpen(true)
                    .openTime("10:00")
                    .closeTime("20:00")
                    .build());
        }
        schedule.add(StoreScheduleDto.builder()
                .dayOfWeek(7)
                .isOpen(false)
                .openTime(null)
                .closeTime(null)
                .build());

        Double dist = null;
        Double storeLat = null;
        Double storeLng = null;
        if (store.getLocationCoord() != null) {
            try {
                String[] coords = store.getLocationCoord().split(",");
                storeLat = Double.parseDouble(coords[0].trim());
                storeLng = Double.parseDouble(coords[1].trim());
                if (latitude != null && longitude != null) {
                    dist = calculateDistanceKm(latitude, longitude, storeLat, storeLng);
                }
            } catch (Exception e) {}
        }

        List<StoreSocial> socials = storeSocialRepository.findByStoreId(storeId);
        String instagram = socials.stream()
                .filter(s -> "INSTAGRAM".equalsIgnoreCase(s.getPlatformName()))
                .map(StoreSocial::getUrl)
                .findFirst().orElse(null);
        String whatsapp = socials.stream()
                .filter(s -> "WHATSAPP".equalsIgnoreCase(s.getPlatformName()))
                .map(StoreSocial::getUrl)
                .findFirst().orElse(null);

        StoreProfileDto dto = StoreProfileDto.builder()
                .id(store.getId())
                .name(store.getBusinessName())
                .description(store.getDescription())
                .address(store.getAddress())
                .latitude(storeLat)
                .longitude(storeLng)
                .ratingAvg(store.getRatingAvg())
                .ratingCount(store.getRatingCount())
                .instagramUrl(instagram)
                .whatsapp(whatsapp)
                .schedule(schedule)
                .isOpenNow(true)
                .reviews(revDtos)
                .distanceKm(dist)
                .shippingCapability("AMBOS")
                .shippingCostBase(1500.0)
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<?> getStoreProducts(
            @PathVariable UUID storeId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        return getCatalogProducts(categoryId, storeId, name, minPrice, maxPrice, null, null, null, null, page, size);
    }

    @GetMapping("/stores/nearby")
    public ResponseEntity<?> getNearbyStores(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(required = false) Boolean openNow) {

        List<Store> stores = storeRepository.findAll();
        List<NearbyStoreDto> dtos = stores.stream()
                .filter(s -> s.getUser().getIsactive())
                .map(s -> {
                    Double dist = null;
                    Double storeLat = null;
                    Double storeLng = null;
                    if (s.getLocationCoord() != null) {
                        try {
                            String[] coords = s.getLocationCoord().split(",");
                            storeLat = Double.parseDouble(coords[0].trim());
                            storeLng = Double.parseDouble(coords[1].trim());
                            dist = calculateDistanceKm(latitude, longitude, storeLat, storeLng);
                        } catch (Exception e) {}
                    }

                    return NearbyStoreDto.builder()
                            .id(s.getId())
                            .name(s.getBusinessName())
                            .address(s.getAddress())
                            .latitude(storeLat)
                            .longitude(storeLng)
                            .ratingAvg(s.getRatingAvg())
                            .ratingCount(s.getRatingCount())
                            .distanceKm(dist)
                            .isOpenNow(true)
                            .shippingCapability("AMBOS")
                            .build();
                })
                .filter(dto -> dto.getDistanceKm() != null)
                .filter(dto -> radiusKm == null || dto.getDistanceKm() <= radiusKm)
                .sorted(Comparator.comparing(NearbyStoreDto::getDistanceKm))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/stores/search")
    public ResponseEntity<?> searchStores(
            @RequestParam String name,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {

        List<Store> stores = storeRepository.findAll();
        String searchLower = name.trim().toLowerCase();
        List<NearbyStoreDto> dtos = stores.stream()
                .filter(s -> s.getUser().getIsactive() && s.getBusinessName().toLowerCase().contains(searchLower))
                .map(s -> {
                    Double dist = null;
                    Double storeLat = null;
                    Double storeLng = null;
                    if (s.getLocationCoord() != null) {
                        try {
                            String[] coords = s.getLocationCoord().split(",");
                            storeLat = Double.parseDouble(coords[0].trim());
                            storeLng = Double.parseDouble(coords[1].trim());
                            if (latitude != null && longitude != null) {
                                dist = calculateDistanceKm(latitude, longitude, storeLat, storeLng);
                            }
                        } catch (Exception e) {}
                    }

                    return NearbyStoreDto.builder()
                            .id(s.getId())
                            .name(s.getBusinessName())
                            .address(s.getAddress())
                            .latitude(storeLat)
                            .longitude(storeLng)
                            .ratingAvg(s.getRatingAvg())
                            .ratingCount(s.getRatingCount())
                            .distanceKm(dist)
                            .isOpenNow(true)
                            .shippingCapability("AMBOS")
                            .build();
                })
                .sorted(Comparator.comparing(dto -> dto.getDistanceKm() != null ? dto.getDistanceKm() : Double.MAX_VALUE))
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ==========================================
    // 3. CHECKOUT & ORDERS
    // ==========================================

    @PostMapping("/checkout/summary")
    public ResponseEntity<?> getCheckoutSummary(@RequestBody CheckoutSummaryRequest req) {
        // Calculate service fee (5% of subtotal, minimum 500)
        double fee = Math.max(req.getProductSubtotal() * 0.05, 500.0);
        double total = req.getProductSubtotal() + req.getQuotedShippingCost() + fee;

        CheckoutSummaryDto summary = CheckoutSummaryDto.builder()
                .productSubtotal(req.getProductSubtotal())
                .shippingCost(req.getQuotedShippingCost())
                .serviceFee(fee)
                .total(total)
                .serviceFeeLabel("Tarifa de servicio OutletGo (5%)")
                .build();
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<?> createOrder(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateOrderRequest req) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        if (req.getStoreGroups() == null || req.getStoreGroups().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El carrito está vacío");
        }

        // Get the first store ID to set in Order table
        UUID firstStoreId = req.getStoreGroups().get(0).getStoreId();
        Store mainStore = storeRepository.findById(firstStoreId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada"));

        // Calculate Totals
        double productSubtotal = 0.0;
        for (CheckoutStorePayload gp : req.getStoreGroups()) {
            for (CheckoutItemPayload item : gp.getItems()) {
                productSubtotal += item.getUnitPrice() * item.getQuantity();
            }
        }
        double shippingCost = req.getShipping().getQuotedCost();
        double serviceFee = Math.max(productSubtotal * 0.05, 500.0);
        double total = productSubtotal + shippingCost + serviceFee;

        // Create Order
        Order order = Order.builder()
                .client(user)
                .store(mainStore)
                .status(Order.OrderStatus.PAID) // Direct paid status for sandbox testing
                .productSubtotal(productSubtotal)
                .shippingCost(shippingCost)
                .serviceFee(serviceFee)
                .totalAmount(total)
                .shippingAddress(req.getShipping().getDeliveryAddress() != null ? req.getShipping().getDeliveryAddress() : "Retiro en punto")
                .orderDate(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // Create OrderStore slices and items
        for (CheckoutStorePayload gp : req.getStoreGroups()) {
            Store store = storeRepository.findById(gp.getStoreId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tienda no encontrada"));

            double groupSubtotal = 0.0;
            for (CheckoutItemPayload item : gp.getItems()) {
                groupSubtotal += item.getUnitPrice() * item.getQuantity();
            }

            OrderStore slice = OrderStore.builder()
                    .order(savedOrder)
                    .store(store)
                    .status(Order.OrderStatus.PAID)
                    .subtotalAmount(groupSubtotal)
                    .commissionRate(0.10)
                    .commissionAmount(groupSubtotal * 0.10)
                    .netAmount(groupSubtotal * 0.90)
                    .payoutStatus("PENDING")
                    .build();

            OrderStore savedSlice = orderStoreRepository.save(slice);

            for (CheckoutItemPayload item : gp.getItems()) {
                ProductVariation variation = productVariationRepository.findById(item.getVariationId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Variación no encontrada"));

                OrderItem orderItem = OrderItem.builder()
                        .orderStore(savedSlice)
                        .variation(variation)
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build();

                orderItemRepository.save(orderItem);

                // Deduct stock
                int newStock = Math.max(0, variation.getStock() - item.getQuantity());
                variation.setStock(newStock);
                productVariationRepository.save(variation);
            }
        }

        // Return MP mock redirect url
        String returnUrl = "outletgo://checkout/return?status=approved&order_id=" + savedOrder.getId();
        return ResponseEntity.ok(CreateOrderResponse.builder()
                .orderId(savedOrder.getId())
                .mpInitPoint(returnUrl)
                .build());
    }

    @GetMapping("/orders")
    public ResponseEntity<?> getOrders(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Order> ordersPage = orderRepository.findByClientIdOrderByOrderDateDesc(user.getId(), pageable);

        List<OrderListItemDto> list = ordersPage.getContent().stream()
                .map(o -> {
                    List<OrderStore> slices = orderStoreRepository.findByOrderId(o.getId());
                    int itemCount = slices.stream()
                            .mapToInt(slice -> orderItemRepository.findByOrderStoreId(slice.getId())
                                    .stream().mapToInt(OrderItem::getQuantity).sum())
                            .sum();

                    return OrderListItemDto.builder()
                            .id(o.getId())
                            .createdAt(o.getOrderDate().toString())
                            .status(o.getStatus().name())
                            .storeCount(slices.size())
                            .itemCount(itemCount)
                            .totalPrice(o.getTotalAmount())
                            .shippingMethod("RETIRO_EN_PUNTO".equals(o.getShippingAddress()) ? "RETIRO_EN_PUNTO" : "ENVIO_CORREO")
                            .build();
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(new PageImpl<>(list, pageable, ordersPage.getTotalElements()));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<?> getOrderDetail(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID orderId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Order o = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido no encontrado"));

        if (!o.getClient().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado");
        }

        List<OrderStore> slices = orderStoreRepository.findByOrderId(orderId);
        List<OrderStoreSliceDto> sliceDtos = slices.stream()
                .map(slice -> {
                    List<OrderItem> items = orderItemRepository.findByOrderStoreId(slice.getId());
                    List<OrderItemDetailDto> itemDtos = items.stream()
                            .map(item -> {
                                List<ProductImage> imgs = productImageRepository.findByProductId(item.getVariation().getProduct().getId());
                                String thumb = imgs.isEmpty() ? null : imgs.get(0).getImageUrl();
                                return OrderItemDetailDto.builder()
                                        .productId(item.getVariation().getProduct().getId())
                                        .productName(item.getVariation().getProduct().getName())
                                        .variationLabel(item.getVariation().getSize() + (item.getVariation().getColor() != null ? " · " + item.getVariation().getColor() : ""))
                                        .quantity(item.getQuantity())
                                        .unitPrice(item.getUnitPrice())
                                        .thumbnailUrl(thumb)
                                        .build();
                            })
                            .collect(Collectors.toList());

                    return OrderStoreSliceDto.builder()
                            .storeId(slice.getStore().getId())
                            .storeName(slice.getStore().getBusinessName())
                            .status(slice.getStatus().name())
                            .refundAmount(slice.getRefundAmount())
                            .items(itemDtos)
                            .build();
                })
                .collect(Collectors.toList());

        OrderDetailDto dto = OrderDetailDto.builder()
                .id(o.getId())
                .createdAt(o.getOrderDate().toString())
                .status(o.getStatus().name())
                .shippingMethod("Retiro en punto".equals(o.getShippingAddress()) ? "RETIRO_EN_PUNTO" : "ENVIO_CORREO")
                .carrier("Retiro en punto".equals(o.getShippingAddress()) ? null : "ANDREANI")
                .trackingNumber("Retiro en punto".equals(o.getShippingAddress()) ? null : "TRK-" + o.getId().toString().substring(0, 8).toUpperCase())
                .productSubtotal(o.getProductSubtotal() != null ? o.getProductSubtotal() : o.getTotalAmount())
                .shippingCost(o.getShippingCost() != null ? o.getShippingCost() : 0.0)
                .serviceFee(o.getServiceFee() != null ? o.getServiceFee() : 0.0)
                .totalPrice(o.getTotalAmount())
                .deliveryAddress(o.getShippingAddress())
                .pickupPoint(null)
                .stores(sliceDtos)
                .build();

        return ResponseEntity.ok(dto);
    }

    // ==========================================
    // 4. CHAT & SUPPORT
    // ==========================================

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<ChatMessage> messages = chatMessageRepository.findBySenderIdOrReceiverIdOrderBySentAtAsc(user.getId());
        Map<UUID, List<ChatMessage>> groups = messages.stream()
                .filter(m -> m.getConversationId() != null)
                .collect(Collectors.groupingBy(ChatMessage::getConversationId));

        List<ConversationListItemDto> list = groups.entrySet().stream()
                .map(e -> {
                    UUID convId = e.getKey();
                    List<ChatMessage> msgs = e.getValue();
                    ChatMessage last = msgs.get(msgs.size() - 1);
                    Store store = last.getStore();

                    return ConversationListItemDto.builder()
                            .id(convId)
                            .storeId(store.getId())
                            .storeName(store.getBusinessName())
                            .storeLogoUrl(store.getHeaderImage())
                            .productId(null)
                            .productName(null)
                            .lastMessagePreview(last.getContent())
                            .lastMessageAt(last.getSentAt().toString())
                            .unreadCount(0)
                            .build();
                })
                .sorted(Comparator.comparing(ConversationListItemDto::getLastMessageAt).reversed())
                .collect(Collectors.toList());

        int start = Math.min(page * size, list.size());
        int end = Math.min((page + 1) * size, list.size());
        List<ConversationListItemDto> paginated = list.subList(start, end);

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(new PageImpl<>(paginated, pageable, list.size()));
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<?> getConversation(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID conversationId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
        if (messages.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversación no encontrada");
        }

        ChatMessage first = messages.get(0);
        Store store = first.getStore();

        ConversationDetailDto dto = ConversationDetailDto.builder()
                .id(conversationId)
                .storeId(store.getId())
                .storeName(store.getBusinessName())
                .storeLogoUrl(store.getHeaderImage())
                .productId(null)
                .productName(null)
                .lastMessagePreview(messages.get(messages.size() - 1).getContent())
                .lastMessageAt(messages.get(messages.size() - 1).getSentAt().toString())
                .unreadCount(0)
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> getConversationMessages(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID conversationId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<ChatMessage> messages = chatMessageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
        List<ChatMessageDto> dtos = messages.stream()
                .map(m -> ChatMessageDto.builder()
                        .id(m.getId())
                        .conversationId(m.getConversationId())
                        .senderRole(m.getSender().getId().equals(user.getId()) ? "BUYER" : "SELLER")
                        .body(m.getContent())
                        .imageUrl(m.getAttachmentUrl())
                        .createdAt(m.getSentAt().toString())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<?> sendChatMessage(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID conversationId,
            @RequestBody ChatMessageDto payload) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<ChatMessage> prev = chatMessageRepository.findByConversationIdOrderBySentAtAsc(conversationId);
        if (prev.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversación no encontrada");
        }

        ChatMessage reference = prev.get(0);
        Store store = reference.getStore();
        User receiver = reference.getSender().getId().equals(user.getId()) ? reference.getReceiver() : reference.getSender();

        ChatMessage message = ChatMessage.builder()
                .conversationId(conversationId)
                .sender(user)
                .receiver(receiver)
                .store(store)
                .content(payload.getBody())
                .attachmentUrl(payload.getImageUrl())
                .attachmentType(payload.getImageUrl() != null ? "IMAGE" : null)
                .sentAt(LocalDateTime.now())
                .build();

        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageDto res = ChatMessageDto.builder()
                .id(saved.getId())
                .conversationId(saved.getConversationId())
                .senderRole("BUYER")
                .body(saved.getContent())
                .imageUrl(saved.getAttachmentUrl())
                .createdAt(saved.getSentAt().toString())
                .build();

        return ResponseEntity.ok(res);
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markRead(@PathVariable UUID conversationId) {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/conversations/start")
    public ResponseEntity<?> startConversation(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> payload) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        UUID storeId = UUID.fromString((String) payload.get("storeId"));
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        // Check if there is an existing conversation between this user and store
        List<ChatMessage> existing = chatMessageRepository.findBySenderIdOrReceiverIdOrderBySentAtAsc(user.getId());
        Optional<UUID> existingConvId = existing.stream()
                .filter(m -> m.getStore().getId().equals(storeId) && m.getConversationId() != null)
                .map(ChatMessage::getConversationId)
                .findFirst();

        UUID convId = existingConvId.orElseGet(UUID::randomUUID);

        if (existingConvId.isEmpty()) {
            // Create a first welcome/placeholder message to save the conversation ID
            ChatMessage welcome = ChatMessage.builder()
                    .conversationId(convId)
                    .sender(store.getUser()) // From Store owner
                    .receiver(user) // To Buyer
                    .store(store)
                    .content("¡Hola! ¿En qué te puedo ayudar hoy?")
                    .sentAt(LocalDateTime.now())
                    .build();
            chatMessageRepository.save(welcome);
        }

        ConversationDetailDto dto = ConversationDetailDto.builder()
                .id(convId)
                .storeId(store.getId())
                .storeName(store.getBusinessName())
                .storeLogoUrl(store.getHeaderImage())
                .productId(null)
                .productName(null)
                .lastMessagePreview("¡Hola! ¿En qué te puedo ayudar hoy?")
                .lastMessageAt(LocalDateTime.now().toString())
                .unreadCount(0)
                .build();

        return ResponseEntity.ok(dto);
    }

    // ==========================================
    // 5. FAVORITES
    // ==========================================

    @GetMapping("/me/favorites/products")
    public ResponseEntity<?> getFavoriteProducts(@RequestHeader("Authorization") String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<ProductFavorite> favs = productFavoriteRepository.findByUserId(user.getId());
        List<Map<String, Object>> list = favs.stream().map(f -> {
            Product p = f.getProduct();
            List<ProductImage> imgs = productImageRepository.findByProductId(p.getId());
            String thumb = imgs.isEmpty() ? null : imgs.get(0).getImageUrl();

            Map<String, Object> map = new HashMap<>();
            map.put("productId", p.getId());
            map.put("productName", p.getName());
            map.put("thumbnailUrl", thumb);
            map.put("price", p.getBasePrice());
            map.put("storeId", p.getStore().getId());
            map.put("storeName", p.getStore().getBusinessName());
            map.put("addedAt", LocalDateTime.now().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    @GetMapping("/me/favorites/stores")
    public ResponseEntity<?> getFavoriteStores(@RequestHeader("Authorization") String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<StoreFavorite> favs = storeFavoriteRepository.findByUserId(user.getId());
        List<Map<String, Object>> list = favs.stream().map(f -> {
            Store s = f.getStore();
            Map<String, Object> map = new HashMap<>();
            map.put("storeId", s.getId());
            map.put("storeName", s.getBusinessName());
            map.put("address", s.getAddress());
            map.put("ratingAvg", s.getRatingAvg());
            map.put("ratingCount", s.getRatingCount());
            map.put("addedAt", LocalDateTime.now().toString());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    @GetMapping("/me/favorites/products/{productId}/status")
    public ResponseEntity<?> getProductFavoriteStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID productId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        boolean isFav = productFavoriteRepository.existsByUserIdAndProductId(user.getId(), productId);
        Map<String, Boolean> res = new HashMap<>();
        res.put("favorite", isFav);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/me/favorites/stores/{storeId}/status")
    public ResponseEntity<?> getStoreFavoriteStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID storeId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        boolean isFav = storeFavoriteRepository.existsByUserIdAndStoreId(user.getId(), storeId);
        Map<String, Boolean> res = new HashMap<>();
        res.put("favorite", isFav);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/me/favorites/products/{productId}")
    public ResponseEntity<Void> addFavoriteProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID productId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        if (!productFavoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            Product p = productRepository.findById(productId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
            productFavoriteRepository.save(ProductFavorite.builder()
                    .user(user)
                    .product(p)
                    .build());
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/favorites/products/{productId}")
    public ResponseEntity<Void> removeFavoriteProduct(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID productId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        productFavoriteRepository.findByUserIdAndProductId(user.getId(), productId)
                .ifPresent(productFavoriteRepository::delete);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/favorites/stores/{storeId}")
    public ResponseEntity<Void> addFavoriteStore(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID storeId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        if (!storeFavoriteRepository.existsByUserIdAndStoreId(user.getId(), storeId)) {
            Store s = storeRepository.findById(storeId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));
            storeFavoriteRepository.save(StoreFavorite.builder()
                    .user(user)
                    .store(s)
                    .build());
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/me/favorites/stores/{storeId}")
    public ResponseEntity<Void> removeFavoriteStore(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID storeId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        storeFavoriteRepository.findByUserIdAndStoreId(user.getId(), storeId)
                .ifPresent(storeFavoriteRepository::delete);

        return ResponseEntity.ok().build();
    }

    // ==========================================
    // 6. REVIEWS & REPORTS
    // ==========================================

    @GetMapping("/orders/{orderId}/reviews")
    public ResponseEntity<?> getOrderReviewStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID orderId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Orden no encontrada"));

        List<OrderStore> slices = orderStoreRepository.findByOrderId(orderId);

        List<Map<String, Object>> products = new ArrayList<>();
        List<Map<String, Object>> stores = new ArrayList<>();

        for (OrderStore slice : slices) {
            List<OrderItem> items = orderItemRepository.findByOrderStoreId(slice.getId());
            for (OrderItem item : items) {
                Product p = item.getVariation().getProduct();
                List<Review> revs = reviewRepository.findByProductId(p.getId());
                Optional<Review> myRev = revs.stream().filter(r -> r.getUser().getId().equals(user.getId())).findFirst();

                Map<String, Object> pMap = new HashMap<>();
                pMap.put("productId", p.getId());
                pMap.put("productName", p.getName());
                pMap.put("variationLabel", item.getVariation().getSize() + (item.getVariation().getColor() != null ? " · " + item.getVariation().getColor() : ""));
                pMap.put("thumbnailUrl", null);
                pMap.put("storeId", slice.getStore().getId());
                pMap.put("storeName", slice.getStore().getBusinessName());
                pMap.put("reviewed", myRev.isPresent());
                pMap.put("review", myRev.map(r -> {
                    Map<String, Object> rMap = new HashMap<>();
                    rMap.put("rating", r.getRating());
                    rMap.put("comment", r.getComment());
                    rMap.put("createdAt", r.getCreatedAt().toString());
                    return rMap;
                }).orElse(null));
                products.add(pMap);
            }

            List<Review> sRevs = reviewRepository.findByStoreId(slice.getStore().getId());
            Optional<Review> myStoreRev = sRevs.stream().filter(r -> r.getUser().getId().equals(user.getId()) && r.getProduct() == null).findFirst();

            Map<String, Object> sMap = new HashMap<>();
            sMap.put("storeId", slice.getStore().getId());
            sMap.put("storeName", slice.getStore().getBusinessName());
            sMap.put("reviewed", myStoreRev.isPresent());
            sMap.put("review", myStoreRev.map(r -> {
                Map<String, Object> rMap = new HashMap<>();
                rMap.put("rating", r.getRating());
                rMap.put("comment", r.getComment());
                rMap.put("createdAt", r.getCreatedAt().toString());
                return rMap;
            }).orElse(null));
            stores.add(sMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("canReview", order.getStatus() == Order.OrderStatus.DELIVERED);
        response.put("products", products);
        response.put("stores", stores);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/orders/{orderId}/reviews/product")
    public ResponseEntity<?> submitProductReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID orderId,
            @RequestBody SubmitReviewRequestDto body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Product p = productRepository.findById(body.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        Review review = Review.builder()
                .product(p)
                .store(p.getStore())
                .user(user)
                .referenceId(orderId)
                .referenceType("ORDER")
                .rating(body.getRating())
                .comment(body.getComment())
                .isVisible(true)
                .createdAt(LocalDateTime.now())
                .build();

        Review saved = reviewRepository.save(review);

        // Update product average rating
        List<Review> pRevs = reviewRepository.findByProductId(p.getId());
        double avg = pRevs.stream().mapToInt(Review::getRating).average().orElse(0.0);
        p.setRatingAvg(avg);
        p.setRatingCount(pRevs.size());
        productRepository.save(p);

        Map<String, Object> res = new HashMap<>();
        res.put("rating", saved.getRating());
        res.put("comment", saved.getComment());
        res.put("createdAt", saved.getCreatedAt().toString());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/orders/{orderId}/reviews/store")
    public ResponseEntity<?> submitStoreReview(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID orderId,
            @RequestBody SubmitReviewRequestDto body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Store store = storeRepository.findById(body.getStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        Review review = Review.builder()
                .product(null) // store review
                .store(store)
                .user(user)
                .referenceId(orderId)
                .referenceType("ORDER")
                .rating(body.getRating())
                .comment(body.getComment())
                .isVisible(true)
                .createdAt(LocalDateTime.now())
                .build();

        Review saved = reviewRepository.save(review);

        // Update store average rating
        List<Review> sRevs = reviewRepository.findByStoreId(store.getId()).stream().filter(r -> r.getProduct() == null).collect(Collectors.toList());
        double avg = sRevs.stream().mapToInt(Review::getRating).average().orElse(0.0);
        store.setRatingAvg(avg);
        store.setRatingCount(sRevs.size());
        storeRepository.save(store);

        Map<String, Object> res = new HashMap<>();
        res.put("rating", saved.getRating());
        res.put("comment", saved.getComment());
        res.put("createdAt", saved.getCreatedAt().toString());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/reports/product/{productId}")
    public ResponseEntity<?> submitProductReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID productId,
            @RequestBody SubmitReportRequestDto body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));

        Report r = Report.builder()
                .reporter(user)
                .product(p)
                .store(null)
                .referenceId(productId)
                .referenceType("PRODUCT")
                .reason(body.getReason() + ": " + (body.getDetails() != null ? body.getDetails() : ""))
                .status(Report.ReportStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        reportRepository.save(r);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reports/store/{storeId}")
    public ResponseEntity<?> submitStoreReport(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID storeId,
            @RequestBody SubmitReportRequestDto body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        Store s = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tienda no encontrada"));

        Report r = Report.builder()
                .reporter(user)
                .product(null)
                .store(s)
                .referenceId(storeId)
                .referenceType("STORE")
                .reason(body.getReason() + ": " + (body.getDetails() != null ? body.getDetails() : ""))
                .status(Report.ReportStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        reportRepository.save(r);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reports")
    public ResponseEntity<?> getMyReports(@RequestHeader("Authorization") String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<Report> reports = reportRepository.findByReporterId(user.getId());
        List<Map<String, Object>> list = reports.stream().map(r -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", r.getId().toString());
            map.put("targetType", r.getReferenceType());
            map.put("targetId", r.getReferenceId());
            map.put("targetName", r.getProduct() != null ? r.getProduct().getName() : (r.getStore() != null ? r.getStore().getBusinessName() : "Objeto"));
            map.put("reason", r.getReason().split(":")[0]);
            map.put("reasonLabel", r.getReason());
            map.put("details", r.getReason());
            map.put("status", r.getStatus().name());
            map.put("adminMessage", r.getResolutionType() != null ? "Resolución: " + r.getResolutionType() : null);
            map.put("createdAt", r.getCreatedAt().toString());
            map.put("resolvedAt", null);
            map.put("seenByBuyer", true);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    @GetMapping("/reports/unread-count")
    public ResponseEntity<?> getUnreadReportCount() {
        Map<String, Integer> map = new HashMap<>();
        map.put("count", 0);
        return ResponseEntity.ok(map);
    }

    @PostMapping("/reports/mark-seen")
    public ResponseEntity<Void> markReportsSeen() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/reports/product/{productId}/status")
    public ResponseEntity<?> getProductReportStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID productId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<Report> reports = reportRepository.findByReporterId(user.getId());
        boolean hasActive = reports.stream()
                .anyMatch(r -> productId.equals(r.getReferenceId()) && (r.getStatus() == Report.ReportStatus.PENDING || r.getStatus() == Report.ReportStatus.REVIEWING));

        Map<String, Object> res = new HashMap<>();
        res.put("hasActiveReport", hasActive);
        res.put("reportId", hasActive ? reports.stream().filter(r -> productId.equals(r.getReferenceId())).findFirst().get().getId() : null);
        return ResponseEntity.ok(res);
    }

    @GetMapping("/reports/store/{storeId}/status")
    public ResponseEntity<?> getStoreReportStatus(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID storeId) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        List<Report> reports = reportRepository.findByReporterId(user.getId());
        boolean hasActive = reports.stream()
                .anyMatch(r -> storeId.equals(r.getReferenceId()) && (r.getStatus() == Report.ReportStatus.PENDING || r.getStatus() == Report.ReportStatus.REVIEWING));

        Map<String, Object> res = new HashMap<>();
        res.put("hasActiveReport", hasActive);
        res.put("reportId", hasActive ? reports.stream().filter(r -> storeId.equals(r.getReferenceId())).findFirst().get().getId() : null);
        return ResponseEntity.ok(res);
    }

    // ==========================================
    // 7. PROFILE & SETTINGS
    // ==========================================

    @PatchMapping("/me")
    public ResponseEntity<?> updateProfile(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateProfileRequest body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        // Return user DTO directly
        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .name(body.getName() != null && !body.getName().trim().isEmpty() ? body.getName().trim() : user.getEmail().split("@")[0])
                .avatarUrl(body.getAvatarUrl())
                .isActive(user.getIsactive())
                .build();
        return ResponseEntity.ok(userDto);
    }

    @PatchMapping("/me/email")
    public ResponseEntity<?> updateEmail(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody UpdateEmailRequest body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        if (!passwordEncoder.matches(body.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contraseña actual incorrecta");
        }

        if (userRepository.existsByEmail(body.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El email ya está en uso");
        }

        user.setEmail(body.getEmail().trim().toLowerCase());
        User saved = userRepository.save(user);

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .role(saved.getRole())
                .name(saved.getEmail().split("@")[0])
                .avatarUrl(null)
                .isActive(saved.getIsactive())
                .build();
        return ResponseEntity.ok(userDto);
    }

    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ChangePasswordRequest body) {

        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }

        if (!passwordEncoder.matches(body.getCurrentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Contraseña actual incorrecta");
        }

        user.setPassword(passwordEncoder.encode(body.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/me/deactivate")
    public ResponseEntity<Void> deactivateAccount(@RequestHeader("Authorization") String authHeader) {
        User user = getAuthenticatedUser(authHeader);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autorizado");
        }
        user.setIsactive(false);
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    // ==========================================
    // 8. SHIPPING & PUSH NOTIFICATIONS
    // ==========================================

    @GetMapping("/shipping/quotes")
    public ResponseEntity<?> getShippingQuotes(
            @RequestParam String postalCode,
            @RequestParam(required = false) Integer weightGrams) {

        List<Map<String, Object>> quotes = new ArrayList<>();
        Map<String, Object> quote1 = new HashMap<>();
        quote1.put("carrier", "CORREO_ARGENTINO");
        quote1.put("cost", 2800.0);
        quote1.put("estimatedDays", 5);

        Map<String, Object> quote2 = new HashMap<>();
        quote2.put("carrier", "ANDREANI");
        quote2.put("cost", 3900.0);
        quote2.put("estimatedDays", 3);

        quotes.add(quote1);
        quotes.add(quote2);

        return ResponseEntity.ok(quotes);
    }

    @GetMapping("/shipping/pickup-points")
    public ResponseEntity<?> getPickupPoints(
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {

        List<PickupPoint> points = pickupPointRepository.findByIsActiveTrue();
        List<Map<String, Object>> list = points.stream().map(p -> {
            Double dist = null;
            if (lat != null && lng != null) {
                dist = calculateDistanceKm(lat, lng, p.getLat(), p.getLng());
            }

            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("address", p.getAddress());
            map.put("neighborhood", p.getNeighborhood());
            map.put("city", p.getCity());
            map.put("lat", p.getLat());
            map.put("lng", p.getLng());
            map.put("businessHours", p.getBusinessHours());
            map.put("distanceKm", dist);
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(list);
    }

    @GetMapping("/orders/{orderId}/shipping-tracking")
    public ResponseEntity<?> getShippingTracking(@PathVariable UUID orderId) {
        Map<String, Object> tracking = new HashMap<>();
        tracking.put("orderId", orderId);
        tracking.put("carrier", "ANDREANI");
        tracking.put("trackingNumber", "TRK-" + orderId.toString().substring(0, 8).toUpperCase());
        tracking.put("currentStatus", "En tránsito");
        tracking.put("estimatedDelivery", LocalDateTime.now().plusDays(2).toString());

        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> e1 = new HashMap<>();
        e1.put("timestamp", LocalDateTime.now().minusDays(1).toString());
        e1.put("description", "Ingresó al centro de distribución");
        e1.put("location", "Avellaneda, Buenos Aires");

        Map<String, Object> e2 = new HashMap<>();
        e2.put("timestamp", LocalDateTime.now().toString());
        e2.put("description", "En camino a destino");
        e2.put("location", null);

        events.add(e1);
        events.add(e2);
        tracking.put("events", events);

        return ResponseEntity.ok(tracking);
    }

    @PostMapping("/notifications/register")
    public ResponseEntity<Void> registerNotifications() {
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/notifications/register")
    public ResponseEntity<Void> unregisterNotifications() {
        return ResponseEntity.ok().build();
    }

    // ==========================================
    // INNER DTO CLASSES
    // ==========================================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CatalogProductDto {
        private UUID id;
        private String name;
        private String thumbnailUrl;
        private Double price;
        private UUID storeId;
        private String storeName;
        private Double ratingAvg;
        private Integer ratingCount;
        private Double distanceKm;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariationDto {
        private UUID id;
        private String size;
        private String color;
        private Integer stock;
        private Double price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewDto {
        private UUID id;
        private Integer rating;
        private String comment;
        private String authorName;
        private String createdAt;
        private Boolean isVisible;
        private List<String> imageUrls;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDetailDto {
        private UUID id;
        private String name;
        private String description;
        private List<String> imageUrls;
        private String thumbnailUrl;
        private Double price;
        private UUID storeId;
        private String storeName;
        private Double ratingAvg;
        private Integer ratingCount;
        private List<VariationDto> variations;
        private List<ReviewDto> reviews;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreScheduleDto {
        private Integer dayOfWeek;
        private Boolean isOpen;
        private String openTime;
        private String closeTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StoreProfileDto {
        private UUID id;
        private String name;
        private String description;
        private String address;
        private Double latitude;
        private Double longitude;
        private Double ratingAvg;
        private Integer ratingCount;
        private String instagramUrl;
        private String whatsapp;
        private List<StoreScheduleDto> schedule;
        private Boolean isOpenNow;
        private List<ReviewDto> reviews;
        private Double distanceKm;
        private String shippingCapability;
        private Double shippingCostBase;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NearbyStoreDto {
        private UUID id;
        private String name;
        private String address;
        private Double latitude;
        private Double longitude;
        private Double ratingAvg;
        private Integer ratingCount;
        private Double distanceKm;
        private Boolean isOpenNow;
        private String shippingCapability;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckoutSummaryDto {
        private Double productSubtotal;
        private Double shippingCost;
        private Double serviceFee;
        private Double total;
        private String serviceFeeLabel;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderListItemDto {
        private UUID id;
        private String createdAt;
        private String status;
        private Integer storeCount;
        private Integer itemCount;
        private Double totalPrice;
        private String shippingMethod;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderDetailDto {
        private UUID id;
        private String createdAt;
        private String status;
        private String shippingMethod;
        private String carrier;
        private String trackingNumber;
        private Double productSubtotal;
        private Double shippingCost;
        private Double serviceFee;
        private Double totalPrice;
        private String deliveryAddress;
        private PickupPointDto pickupPoint;
        private List<OrderStoreSliceDto> stores;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PickupPointDto {
        private UUID id;
        private String name;
        private String address;
        private String neighborhood;
        private String businessHours;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderStoreSliceDto {
        private UUID storeId;
        private String storeName;
        private String status;
        private Double refundAmount;
        private List<OrderItemDetailDto> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDetailDto {
        private UUID productId;
        private String productName;
        private String variationLabel;
        private Integer quantity;
        private Double unitPrice;
        private String thumbnailUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationListItemDto {
        private UUID id;
        private UUID storeId;
        private String storeName;
        private String storeLogoUrl;
        private UUID productId;
        private String productName;
        private String lastMessagePreview;
        private String lastMessageAt;
        private Integer unreadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConversationDetailDto {
        private UUID id;
        private UUID storeId;
        private String storeName;
        private String storeLogoUrl;
        private UUID productId;
        private String productName;
        private String lastMessagePreview;
        private String lastMessageAt;
        private Integer unreadCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageDto {
        private UUID id;
        private UUID conversationId;
        private String senderRole;
        private String body;
        private String imageUrl;
        private String createdAt;
    }
}
