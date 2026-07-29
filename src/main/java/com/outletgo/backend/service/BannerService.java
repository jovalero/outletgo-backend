package com.outletgo.backend.service;

import com.outletgo.backend.entity.Banner;
import com.outletgo.backend.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BannerService {

    private final BannerRepository bannerRepository;

    public Page<Banner> getBanners(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return bannerRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    @Transactional
    public Banner createBanner(Banner banner) {
        if (banner.getId() == null) {
            banner.setId(UUID.randomUUID());
        }
        return bannerRepository.save(banner);
    }
}
