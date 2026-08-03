package com.outletgo.backend.controller;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.service.BannerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<?> getBanners(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            Page<Banner> result = bannerService.getBanners(page, size);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error fetching banners: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }

    @PostMapping
    public ResponseEntity<?> createBanner(@RequestBody Banner banner) {
        try {
            Banner created = bannerService.createBanner(banner);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Error creating banner: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }
}
