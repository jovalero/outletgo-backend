package com.outletgo.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleCallbackRequest {

    @NotBlank(message = "El email es requerido")
    @Email(message = "Email inválido")
    private String email;

    private String name;

    private String avatarUrl;
}
