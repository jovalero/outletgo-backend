package com.outletgo.backend.service;

import com.outletgo.backend.entity.SystemSetting;
import com.outletgo.backend.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SystemSettingService {

    private final SystemSettingRepository systemSettingRepository;

    public Optional<String> getSettingValue(String key) {
        return systemSettingRepository.findById(key).map(SystemSetting::getSettingValue);
    }

    @Transactional
    public SystemSetting saveSetting(String key, String value) {
        SystemSetting setting = SystemSetting.builder()
                .settingKey(key)
                .settingValue(value)
                .build();
        return systemSettingRepository.save(setting);
    }
}
