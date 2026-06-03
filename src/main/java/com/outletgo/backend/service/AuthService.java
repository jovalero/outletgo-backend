package com.outletgo.backend.service;

import com.outletgo.backend.config.JwtUtil;
import com.outletgo.backend.dto.AuthResponse;
import com.outletgo.backend.dto.LoginRequest;
import com.outletgo.backend.dto.RegisterRequest;
import com.outletgo.backend.entity.Store;
import com.outletgo.backend.entity.User;
import com.outletgo.backend.entity.User.Role;
import com.outletgo.backend.exception.BadRequestException;
import com.outletgo.backend.repository.StoreRepository;
import com.outletgo.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("El email ya está registrado");
        }

        // Create and save User
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .isactive(true)
                .build();

        User savedUser = userRepository.save(user);

        // Proactive business feature: If the user is an OUTLET_OWNER, create an empty Store profile
        if (request.getRole() == Role.OUTLET_OWNER) {
            Store store = Store.builder()
                    .user(savedUser)
                    .businessName("Mi Outlet (" + request.getEmail() + ")")
                    .cuit("00-00000000-0")
                    .address("Dirección a definir")
                    .description("Descripción de mi tienda outlet")
                    .ratingAvg(0.0)
                    .ratingCount(0)
                    .build();
            storeRepository.save(store);
        }

        // Generate Token
        String token = jwtUtil.generateToken(savedUser.getId(), savedUser.getEmail(), savedUser.getRole().name());

        UUID storeId = null;
        if (savedUser.getRole() == Role.OUTLET_OWNER) {
            storeId = storeRepository.findByUserId(savedUser.getId())
                    .map(Store::getId)
                    .orElse(null);
        }

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .name(savedUser.getEmail().split("@")[0])
                .storeId(storeId)
                .avatarUrl(null)
                .isActive(savedUser.getIsactive())
                .build();

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadRequestException("Credenciales inválidas"));

        if (!user.getIsactive()) {
            throw new BadRequestException("Esta cuenta de usuario ha sido desactivada");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadRequestException("Credenciales inválidas");
        }

        // Generate Token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        UUID storeId = null;
        if (user.getRole() == Role.OUTLET_OWNER) {
            storeId = storeRepository.findByUserId(user.getId())
                    .map(Store::getId)
                    .orElse(null);
        }

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .name(user.getEmail().split("@")[0])
                .storeId(storeId)
                .avatarUrl(null)
                .isActive(user.getIsactive())
                .build();

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .build();
    }
}
