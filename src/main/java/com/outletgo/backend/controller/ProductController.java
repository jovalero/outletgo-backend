package com.outletgo.backend.controller;

import com.outletgo.backend.dto.ProductDTO;
import com.outletgo.backend.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getAllProducts() {
        List<ProductDTO> products = productService.getAllActiveProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProductById(@PathVariable UUID id) {
        ProductDTO product = productService.getProductById(id);
        return ResponseEntity.ok(product);
    }

    @GetMapping("/store/{storeId}")
    public ResponseEntity<List<ProductDTO>> getProductsByStore(@PathVariable UUID storeId) {
        List<ProductDTO> products = productService.getProductsByStore(storeId);
        return ResponseEntity.ok(products);
    }
}
