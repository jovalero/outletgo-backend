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
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BadRequestException("El email ya está registrado");
        }

        // Create and save User
        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .isactive(true)
                .build();

        User savedUser = userRepository.save(user);

        // Proactive business feature: If the user is an OUTLET_OWNER, create an empty Store profile
        if (request.getRole() == Role.OUTLET_OWNER) {
            Store store = Store.builder()
                    .user(savedUser)
                    .businessName("Mi Outlet (" + email + ")")
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
        String email = request.getEmail() != null ? request.getEmail().trim() : "";
        User user = userRepository.findFirstByEmailIgnoreCase(email)
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
    @Transactional
    public AuthResponse loginOrRegisterWithGoogle(String email, String name, String avatarUrl) {
        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";

        // Buscar si el usuario ya existe (registro previo con email/pass o Google)
        User user = userRepository.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            // Extraer nombre y apellido de Google
            String firstName = null;
            String lastName = null;
            if (name != null && !name.trim().isEmpty()) {
                String[] parts = name.trim().split(" ", 2);
                firstName = parts[0];
                if (parts.length > 1) {
                    lastName = parts[1];
                }
            } else {
                firstName = normalizedEmail.split("@")[0];
            }

            // Primera vez que ingresa con Google → registrar automáticamente como CLIENT
            user = User.builder()
                    .email(normalizedEmail)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString())) // password inutilizable
                    .role(Role.CLIENT)
                    .isactive(true)
                    .name(firstName)
                    .lastName(lastName)
                    .avatarUrl(avatarUrl)
                    .build();
            user = userRepository.save(user);
        }

        if (!user.getIsactive()) {
            throw new BadRequestException("Esta cuenta de usuario ha sido desactivada");
        }

        // Generar nuestro propio JWT (idéntico al login normal)
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole().name());

        AuthResponse.UserDto userDto = AuthResponse.UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole())
                .name(user.getName() != null ? user.getName() : user.getEmail().split("@")[0])
                .lastName(user.getLastName())
                .avatarUrl(user.getAvatarUrl())
                .isActive(user.getIsactive())
                .build();

        return AuthResponse.builder()
                .token(token)
                .user(userDto)
                .build();
    }
}
