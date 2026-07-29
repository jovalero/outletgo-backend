package com.outletgo.backend.controller;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AdminBannerController {

    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<Page<Banner>> getBanners(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(bannerService.getBanners(page, size));
    }

    @PostMapping
    public ResponseEntity<Banner> createBanner(@RequestBody Banner banner) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bannerService.createBanner(banner));
    }
}
