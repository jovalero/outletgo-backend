package com.outletgo.backend.controller;

import com.outletgo.backend.entity.SystemSetting;
import com.outletgo.backend.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemSettingController {

    private final SystemSettingService systemSettingService;

    @GetMapping("/api/settings/{key}")
    public ResponseEntity<Map<String, String>> getSetting(@PathVariable("key") String key) {
        return systemSettingService.getSettingValue(key)
                .map(val -> ResponseEntity.ok(Map.of("key", key, "value", val)))
                .orElse(ResponseEntity.ok(Map.of("key", key, "value", "")));
    }

    @PutMapping("/api/admin/settings/{key}")
    public ResponseEntity<SystemSetting> updateSetting(
            @PathVariable("key") String key,
            @RequestBody Map<String, String> body) {
        String value = body.getOrDefault("value", "");
        return ResponseEntity.ok(systemSettingService.saveSetting(key, value));
    }
}
