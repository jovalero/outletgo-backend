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
        return bannerRepository.findAll(pageable);
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
    public void deleteBanner(UUID id) {
        if (bannerRepository.existsById(id)) {
            bannerRepository.deleteById(id);
        }
    }

    public List<Banner> getActiveBanners() {
        LocalDateTime now = LocalDateTime.now();
        return bannerRepository.findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByCreatedAtDesc(
                "ACTIVE", now, now);
    }

    public Optional<Banner> getBannerById(UUID id) {
        return bannerRepository.findById(id);
    }
}
