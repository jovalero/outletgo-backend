package com.outletgo.backend.controller;

import com.outletgo.backend.dto.AuthResponse;
import com.outletgo.backend.dto.GoogleCallbackRequest;
import com.outletgo.backend.dto.LoginRequest;
import com.outletgo.backend.dto.RegisterRequest;
import com.outletgo.backend.service.AuthService;
import com.outletgo.backend.config.JwtUtil;
import com.outletgo.backend.entity.User;
import com.outletgo.backend.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No autorizado");
        }
        String token = authHeader.substring(7);
        try {
            Claims claims = jwtUtil.validateToken(token);
            UUID userId = UUID.fromString(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

            AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                    .id(user.getId())
                    .email(user.getEmail())
                    .role(user.getRole())
                    .name(user.getName() != null ? user.getName() : user.getEmail().split("@")[0])
                    .lastName(user.getLastName())
                    .avatarUrl(user.getAvatarUrl())
                    .isActive(user.getIsactive())
                    .authProvider(user.getAuthProvider() != null ? user.getAuthProvider().name() : "LOCAL")
                    .build();
            return ResponseEntity.ok(userDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token inválido");
        }
    }

    @PostMapping("/google/callback")
    public ResponseEntity<AuthResponse> googleCallback(@Valid @RequestBody GoogleCallbackRequest request) {
        AuthResponse response = authService.loginOrRegisterWithGoogle(
                request.getEmail(),
                request.getName(),
                request.getAvatarUrl()
        );
        return ResponseEntity.ok(response);
    }
}
