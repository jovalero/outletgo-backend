package com.outletgo.backend.service;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;

    @Transactional(readOnly = true)
    public Page<Banner> getBanners(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return bannerRepository.findAllWithRelations(pageable);
    }

    @Transactional
    public Banner createBanner(Banner banner) {
        banner.setId(null);
        return bannerRepository.save(banner);
    }

    @Transactional
    public Banner updateBannerStatus(UUID id, String status) {
        Optional<Banner> optional = bannerRepository.findById(id);
        if (optional.isPresent()) {
            Banner banner = optional.get();
            banner.setStatus(status);
            return bannerRepository.save(banner);
        }
        return null;
    }

    @Transactional
    public Banner updateBanner(UUID id, Banner newBannerData) {
        Optional<Banner> optional = bannerRepository.findById(id);
        if (optional.isPresent()) {
            Banner banner = optional.get();
            if (newBannerData.getTitle() != null) banner.setTitle(newBannerData.getTitle());
            if (newBannerData.getDescription() != null) banner.setDescription(newBannerData.getDescription());
            if (newBannerData.getImageUrl() != null) banner.setImageUrl(newBannerData.getImageUrl());
            if (newBannerData.getType() != null) banner.setType(newBannerData.getType());
            if (newBannerData.getStartDate() != null) banner.setStartDate(newBannerData.getStartDate());
            if (newBannerData.getEndDate() != null) banner.setEndDate(newBannerData.getEndDate());
            if (newBannerData.getStatus() != null) banner.setStatus(newBannerData.getStatus());
            if (newBannerData.getBadgeText() != null) banner.setBadgeText(newBannerData.getBadgeText());
            if (newBannerData.getStores() != null) banner.setStores(newBannerData.getStores());
            if (newBannerData.getProducts() != null) banner.setProducts(newBannerData.getProducts());
            return bannerRepository.save(banner);
        }
        return null;
    }

    @Transactional
    public void deleteBanner(UUID id) {
        if (bannerRepository.existsById(id)) {
            bannerRepository.deleteById(id);
        }
    }

    @Transactional(readOnly = true)
    public List<Banner> getActiveBanners() {
        LocalDateTime now = LocalDateTime.now();
        List<Banner> banners = bannerRepository.findActiveBanners(now);
        if (banners == null || banners.isEmpty()) {
            banners = bannerRepository.findByStatusOrderByCreatedAtDesc("ACTIVE");
        }
        return banners;
    }

    public Optional<Banner> getBannerById(UUID id) {
        return bannerRepository.findById(id);
    }
}
