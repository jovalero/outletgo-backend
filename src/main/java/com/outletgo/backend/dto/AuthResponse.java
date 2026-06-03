package com.outletgo.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.outletgo.backend.entity.User.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UserDto user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserDto {
        private UUID id;
        private String email;
        private Role role;
        private String name;
        private UUID storeId;
        private String avatarUrl;
        
        @JsonProperty("isActive")
        private boolean isActive;
    }
}
