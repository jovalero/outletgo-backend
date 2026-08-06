package com.outletgo.backend.config;

import com.outletgo.backend.entity.*;
import com.outletgo.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Random;

@Component
@RequiredArgsConstructor
@Slf4j
public class StoreDataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariationRepository productVariationRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        log.info("Verificando si es necesario inicializar tiendas de prueba...");

        if (storeRepository.count() >= 5) {
            log.info("Ya existen tiendas suficientes, no se generarán nuevas tiendas.");
            return;
        }

        log.info("Generando tiendas, productos y valoraciones de prueba...");
        Random random = new Random();

        // Crear categorías si no existen
        List<String> categoryNames = List.of("cat-ropa", "cat-calzado", "cat-accesorios", "cat-abrigos");
        List<Category> categories = new ArrayList<>();
        for (String name : categoryNames) {
            Category cat = categoryRepository.findByName(name).orElse(null);
            if (cat == null) {
                cat = Category.builder().name(name).build();
                categoryRepository.save(cat);
            }
            categories.add(cat);
        }

        String[] storeNames = {
            "Moda Urbana BA", "Calzado Premium Store", "Accesorios Vintage", 
            "Outlet Deportivo Central", "Textil Norte Mayorista"
        };
        
        String[] avatars = {
            "https://images.unsplash.com/photo-1441984904996-e0b6ba687e04?q=80&w=200",
            "https://images.unsplash.com/photo-1555529771-835f59bfc50c?q=80&w=200",
            "https://images.unsplash.com/photo-1528698827591-e19ccd7bc23d?q=80&w=200",
            "https://images.unsplash.com/photo-1497339100210-9e87df79c218?q=80&w=200",
            "https://images.unsplash.com/photo-1567401893414-76b7b1e5a7a5?q=80&w=200"
        };

        String[] productImages = {
            "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?q=80&w=600",
            "https://images.unsplash.com/photo-1583743814966-8936f5b7be1a?q=80&w=600",
            "https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=600",
            "https://images.unsplash.com/photo-1562157873-818bc0726f68?q=80&w=600",
            "https://images.unsplash.com/photo-1591047139829-d91aecb6caea?q=80&w=600"
        };

        String[] productNames = {
            "Remera Estampada Exclusiva", "Buzo Oversize Frizado", "Pantalón Cargo Ajustable",
            "Zapatillas Urban Runner", "Campera Rompevientos Ligera"
        };

        // Crear un usuario cliente base para dejar reseñas
        User reviewer = userRepository.findByEmail("reviewer@outletgo.com").orElse(null);
        if (reviewer == null) {
            reviewer = User.builder()
                .email("reviewer@outletgo.com")
                .password(passwordEncoder.encode("123456"))
                .role(User.Role.CLIENT)
                .name("Reviewer")
                .lastName("Test")
                .build();
            userRepository.save(reviewer);
        }

        // Crear 5 tiendas
        for (int i = 0; i < 5; i++) {
            String email = "store" + i + "@outletgo.com";
            User owner = userRepository.findByEmail(email).orElse(null);
            
            if (owner == null) {
                owner = User.builder()
                    .email(email)
                    .password(passwordEncoder.encode("123456"))
                    .role(User.Role.OUTLET_OWNER)
                    .name("Dueño")
                    .lastName("Tienda " + i)
                    .build();
                userRepository.save(owner);

                Store store = Store.builder()
                    .user(owner)
                    .businessName(storeNames[i])
                    .cuit("20-12345678-" + i)
                    .description("La mejor tienda de ropa de la zona.")
                    .address("Av. Corrientes " + (1000 + (i * 150)))
                    .locationCoord("-34.6037,-58.3816") // Obelisco BA aprox
                    .headerImage(avatars[i])
                    .ratingAvg(0.0)
                    .ratingCount(0)
                    .build();
                storeRepository.save(store);

                // Crear 5 productos para esta tienda
                for (int j = 0; j < 5; j++) {
                    Product product = Product.builder()
                        .store(store)
                        .category(categories.get(random.nextInt(categories.size())))
                        .name(productNames[j] + " - " + storeNames[i])
                        .description("Producto excelente con detalles premium.")
                        .basePrice(5000.0 + random.nextInt(20000))
                        .ratingAvg(0.0)
                        .ratingCount(0)
                        .isactive(true)
                        .build();
                    productRepository.save(product);

                    // Variaciones de talle
                    String[] sizes = {"S", "M", "L", "XL"};
                    for (String size : sizes) {
                        ProductVariation pv = ProductVariation.builder()
                            .product(product)
                            .size(size)
                            .color("Negro")
                            .stock(10 + random.nextInt(40))
                            .build();
                        productVariationRepository.save(pv);
                    }

                    // Imagen del producto
                    ProductImage pImg = ProductImage.builder()
                        .product(product)
                        .imageUrl(productImages[random.nextInt(productImages.length)])
                        .build();
                    productImageRepository.save(pImg);

                    // Añadir de 1 a 5 reseñas aleatorias para el producto y la tienda
                    int numReviews = 1 + random.nextInt(5);
                    double totalRating = 0;
                    for (int r = 0; r < numReviews; r++) {
                        int ratingVal = 3 + random.nextInt(3); // 3 a 5 estrellas
                        totalRating += ratingVal;
                        Review rev = Review.builder()
                            .product(product)
                            .store(store)
                            .user(reviewer)
                            .rating(ratingVal)
                            .comment("Muy buen producto, altamente recomendado!")
                            .isVisible(true)
                            .createdAt(LocalDateTime.now().minusDays(random.nextInt(30)))
                            .build();
                        reviewRepository.save(rev);
                    }
                    product.setRatingAvg(Math.round((totalRating / numReviews) * 10.0) / 10.0);
                    product.setRatingCount(numReviews);
                    productRepository.save(product);
                }

                // Añadir reseñas directas a la tienda
                for (int sr = 0; sr < 2; sr++) {
                    Review storeRev = Review.builder()
                        .product(null)
                        .store(store)
                        .user(reviewer)
                        .rating(4 + random.nextInt(2))
                        .comment("Excelente atención y envío rapidísimo en la tienda!")
                        .isVisible(true)
                        .createdAt(LocalDateTime.now().minusDays(random.nextInt(15)))
                        .build();
                    reviewRepository.save(storeRev);
                }

                // Calcular promedio tienda
                Double storeAvg = reviewRepository.getAverageRatingForStore(store.getId());
                Long storeCount = reviewRepository.countReviewsForStore(store.getId());
                if (storeAvg != null) store.setRatingAvg(Math.round(storeAvg * 10.0) / 10.0);
                if (storeCount != null) store.setRatingCount(storeCount.intValue());
                storeRepository.save(store);
            }
        }
        
        // Sincronizar promedio de calificación real para TODAS las tiendas en Supabase
        log.info("Sincronizando promedios de calificación reales para todas las tiendas...");
        for (Store s : storeRepository.findAll()) {
            Double avg = reviewRepository.getAverageRatingForStore(s.getId());
            Long count = reviewRepository.countReviewsForStore(s.getId());
            if (count != null && count > 0 && avg != null) {
                s.setRatingAvg(Math.round(avg * 10.0) / 10.0);
                s.setRatingCount(count.intValue());
            } else {
                s.setRatingAvg(0.0);
                s.setRatingCount(0);
            }
            storeRepository.save(s);
        }

        log.info("Datos de tiendas de prueba generados y sincronizados exitosamente.");
    }
}
