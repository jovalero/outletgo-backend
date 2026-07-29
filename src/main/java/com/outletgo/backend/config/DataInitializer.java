package com.outletgo.backend.config;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.entity.BlogArticle;
import com.outletgo.backend.entity.BlogCategory;
import com.outletgo.backend.entity.SystemSetting;
import com.outletgo.backend.repository.BannerRepository;
import com.outletgo.backend.repository.BlogArticleRepository;
import com.outletgo.backend.repository.BlogCategoryRepository;
import com.outletgo.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final BlogCategoryRepository blogCategoryRepository;
    private final BlogArticleRepository blogArticleRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final BannerRepository bannerRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Inicializando datos por defecto en OutletGo Backend...");

        // 1. Categorías por defecto
        if (blogCategoryRepository.count() == 0) {
            List<String> defaultCats = List.of("Guías de Compra", "Cuidado Textil", "Pymes Textiles", "Tendencias");
            for (String catName : defaultCats) {
                blogCategoryRepository.save(BlogCategory.builder()
                        .id(UUID.randomUUID())
                        .name(catName)
                        .build());
            }
            log.info("Categorías de blog inicializadas.");
        }

        // 2. Artículos por defecto
        if (blogArticleRepository.count() == 0) {
            blogArticleRepository.save(BlogArticle.builder()
                    .id(UUID.randomUUID())
                    .title("Símbolos de Lavado en la Etiqueta: Guía Definitiva 2026")
                    .category("Cuidado Textil")
                    .date("24 de Julio, 2026")
                    .author("Por Equipo OutletGo")
                    .image("/review_oversize_tee.png")
                    .color("#2B8FD4")
                    .status("PUBLISHED")
                    .content(List.of(
                            "¿Alguna vez arruinaste tu remera oversize favorita o encogiste un buzo frizado por no entender la etiqueta de lavado?",
                            "La tina con agua indica la temperatura máxima recomendada. Un punto dentro significa lavado con agua fría (máx 30°C).",
                            "El triángulo indica el uso de blanqueadores. Si tiene una cruz encima, está prohibido usar lavandina.",
                            "El cuadrado con un círculo adentro representa la secadora de ropa. Un punto significa secado suave."
                    ))
                    .build());

            blogArticleRepository.save(BlogArticle.builder()
                    .id(UUID.randomUUID())
                    .title("Cómo Comprar Indumentaria de Segunda Selección y Discontinuos con Éxito")
                    .category("Guías de Compra")
                    .date("19 de Julio, 2026")
                    .author("Por Sofia Valenzuela")
                    .image("/review_hoodie.png")
                    .color("#10B981")
                    .status("PUBLISHED")
                    .content(List.of(
                            "Comprar ropa discontinua de temporadas pasadas es la mejor estrategia para renovar el guardarropa sin gastar de más.",
                            "Verificá siempre las medidas en cm en lugar del talle comercial.",
                            "Revisá los detalles de costura y cierres. Muchas veces los saldos solo tienen pequeños detalles de estampa."
                    ))
                    .build());

            blogArticleRepository.save(BlogArticle.builder()
                    .id(UUID.randomUUID())
                    .title("Guía para Comercios: Cómo Liquidad Sobrestock sin Canibalizar tu Local")
                    .category("Pymes Textiles")
                    .date("15 de Julio, 2026")
                    .author("Por Martin Gomez")
                    .image("/review_sneakers.png")
                    .color("#6366F1")
                    .status("PUBLISHED")
                    .content(List.of(
                            "Tener prendas estancadas de la temporada anterior inmoviliza el capital de trabajo de tu marca.",
                            "Publicar en un canal exclusivo de outlet te permite dar salida a esos productos a precios tentadores sin devaluar la vidriera de tu local principal."
                    ))
                    .build());

            log.info("Artículos de blog por defecto inicializados.");
        }

        // 3. Configuración del Video B2B por defecto
        if (!systemSettingRepository.existsById("b2b-video-url")) {
            systemSettingRepository.save(SystemSetting.builder()
                    .settingKey("b2b-video-url")
                    .settingValue("https://www.youtube.com/embed/8tCq3330N1o")
                    .build());
            log.info("Configuración de video B2B inicializada.");
        }

        // 4. Banners Promocionales por defecto
        if (bannerRepository.count() == 0) {
            bannerRepository.save(Banner.builder()
                    .id(UUID.randomUUID())
                    .title("Gran Campaña de Invierno")
                    .description("Prendas y tiendas seleccionadas con hasta 50% de descuento")
                    .imageUrl("https://images.unsplash.com/photo-1483985988355-763728e1935b?q=80&w=600&auto=format&fit=crop")
                    .type("CAMPAIGN")
                    .status("ACTIVE")
                    .build());

            bannerRepository.save(Banner.builder()
                    .id(UUID.randomUUID())
                    .title("Día del Zapato")
                    .description("Todo el calzado participante reunido en un solo lugar")
                    .imageUrl("https://images.unsplash.com/photo-1549298916-b41d501d3772?q=80&w=600&auto=format&fit=crop")
                    .type("CAMPAIGN")
                    .status("ACTIVE")
                    .build());

            log.info("Banners promocionales por defecto inicializados.");
        }
    }
}
