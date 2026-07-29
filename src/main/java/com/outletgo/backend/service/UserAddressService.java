package com.outletgo.backend.service;

import com.outletgo.backend.entity.User;
import com.outletgo.backend.entity.UserAddress;
import com.outletgo.backend.entity.PickupPoint;
import com.outletgo.backend.repository.UserRepository;
import com.outletgo.backend.repository.UserAddressRepository;
import com.outletgo.backend.repository.PickupPointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final UserRepository userRepository;
    private final PickupPointRepository pickupPointRepository;

    public List<UserAddress> getAddressesByUserId(UUID userId) {
        return userAddressRepository.findByUserId(userId);
    }

    @Transactional
    public UserAddress createAddress(UUID userId, UserAddress address) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        address.setUser(user);

        List<UserAddress> existing = userAddressRepository.findByUserId(userId);
        if (existing.isEmpty()) {
            address.setIsDefault(true);
        } else if (Boolean.TRUE.equals(address.getIsDefault())) {
            resetDefaultAddresses(userId);
        } else {
            address.setIsDefault(false);
        }

        return userAddressRepository.save(address);
    }

    @Transactional
    public UserAddress updateAddress(UUID userId, Long addressId, UserAddress updatedData) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Dirección no encontrada o acceso denegado"));

        address.setName(updatedData.getName());
        address.setStreet(updatedData.getStreet());
        address.setNumber(updatedData.getNumber());
        address.setApartment(updatedData.getApartment());
        address.setPostalCode(updatedData.getPostalCode());
        address.setCity(updatedData.getCity());
        address.setLatitude(updatedData.getLatitude());
        address.setLongitude(updatedData.getLongitude());

        if (Boolean.TRUE.equals(updatedData.getIsDefault())) {
            if (!Boolean.TRUE.equals(address.getIsDefault())) {
                resetDefaultAddresses(userId);
                address.setIsDefault(true);
            }
        }

        return userAddressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, Long addressId) {
        UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Dirección no encontrada o acceso denegado"));

        boolean wasDefault = Boolean.TRUE.equals(address.getIsDefault());
        userAddressRepository.delete(address);

        if (wasDefault) {
            List<UserAddress> remaining = userAddressRepository.findByUserId(userId);
            if (!remaining.isEmpty()) {
                UserAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                userAddressRepository.save(newDefault);
            }
        }
    }

    @Transactional
    public User updateLogisticsPreference(UUID userId, String type, String referenceId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if ("DELIVERY".equalsIgnoreCase(type)) {
            Long addressId = Long.parseLong(referenceId);
            UserAddress address = userAddressRepository.findByIdAndUserId(addressId, userId)
                    .orElseThrow(() -> new RuntimeException("Dirección no encontrada"));
            user.setSelectedLogisticsType("DELIVERY");
            user.setSelectedAddress(address);
            user.setSelectedPickupPoint(null);
        } else if ("PICKUP".equalsIgnoreCase(type)) {
            PickupPoint pickupPoint = pickupPointRepository.findById(referenceId)
                    .orElseThrow(() -> new RuntimeException("Punto de retiro no encontrado"));
            user.setSelectedLogisticsType("PICKUP");
            user.setSelectedPickupPoint(pickupPoint);
            user.setSelectedAddress(null);
        } else {
            throw new IllegalArgumentException("Tipo de logística inválido: " + type);
        }

        return userRepository.save(user);
    }

    private void resetDefaultAddresses(UUID userId) {
        List<UserAddress> addresses = userAddressRepository.findByUserId(userId);
        for (UserAddress addr : addresses) {
            if (Boolean.TRUE.equals(addr.getIsDefault())) {
                addr.setIsDefault(false);
                userAddressRepository.save(addr);
            }
        }
    }
}
