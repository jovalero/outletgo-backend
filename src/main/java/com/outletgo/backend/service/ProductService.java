package com.outletgo.backend.service;

import com.outletgo.backend.dto.ProductDTO;
import com.outletgo.backend.dto.VariationDTO;
import com.outletgo.backend.entity.Product;
import com.outletgo.backend.entity.ProductImage;
import com.outletgo.backend.entity.ProductVariation;
import com.outletgo.backend.entity.Tag;
import com.outletgo.backend.exception.BadRequestException;
import com.outletgo.backend.repository.ProductImageRepository;
import com.outletgo.backend.repository.ProductRepository;
import com.outletgo.backend.repository.ProductVariationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private ProductVariationRepository productVariationRepository;

    @Transactional(readOnly = true)
    public List<ProductDTO> getAllActiveProducts() {
        List<Product> products = productRepository.findByIsactiveTrue();
        return products.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductDTO getProductById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Producto no encontrado"));
        return mapToDTO(product);
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> getProductsByStore(UUID storeId) {
        List<Product> products = productRepository.findByStoreId(storeId);
        return products.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    private ProductDTO mapToDTO(Product product) {
        // Fetch related images
        List<String> imageUrls = productImageRepository.findByProductId(product.getId())
                .stream()
                .map(ProductImage::getImageUrl)
                .collect(Collectors.toList());

        // Map tags
        List<String> tags = product.getTags() != null ? 
                product.getTags().stream().map(Tag::getTagName).collect(Collectors.toList()) : 
                List.of();

        // Map variations
        List<VariationDTO> variations = productVariationRepository.findByProductId(product.getId())
                .stream()
                .map(v -> VariationDTO.builder()
                        .id(v.getId())
                        .size(v.getSize())
                        .color(v.getColor())
                        .stock(v.getStock())
                        .build())
                .collect(Collectors.toList());

        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .basePrice(product.getBasePrice())
                .ratingAvg(product.getRatingAvg())
                .ratingCount(product.getRatingCount())
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : "Sin Categoría")
                .storeName(product.getStore() != null ? product.getStore().getBusinessName() : "Sin Local")
                .imageUrls(imageUrls)
                .tags(tags)
                .variations(variations)
                .build();
    }
}
