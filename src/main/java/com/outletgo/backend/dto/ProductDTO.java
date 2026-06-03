package com.outletgo.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private UUID id;
    private String name;
    private String description;
    private Double basePrice;
    private Double ratingAvg;
    private Integer ratingCount;
    private String categoryName;
    private String storeName;
    private List<String> imageUrls;
    private List<String> tags;
    private List<VariationDTO> variations;
}
