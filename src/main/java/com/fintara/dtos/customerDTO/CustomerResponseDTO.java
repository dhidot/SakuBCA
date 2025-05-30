package com.fintara.dtos.customerDTO;

import com.fintara.models.CustomerDetails;
import com.fintara.models.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CustomerResponseDTO {
    private UUID id;
    private String email;
    private String name;
    private String role;
    private String userType;
    private List<String> roleFeatures;
    private String plafond; // Tambahkan informasi plafond
    private BigDecimal remainingPlafond;

    // 🔹 Static method untuk konversi dari User ke CustomerResponseDTO
    public static CustomerResponseDTO fromUser(User user, CustomerDetails customerDetails) {
        List<String> featureNames = user.getRole().getRoleFeatures().stream()
                .map(roleFeature -> roleFeature.getFeatureName())  // Ambil nama fitur dari RoleFeature
                .collect(Collectors.toList());
        return new CustomerResponseDTO(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole().getName(),
                user.getUserType().name(),
                featureNames,  // Pastikan role memiliki roleFeatures
                customerDetails.getPlafond().getName(), // Ambil nama plafond dari CustomerDetails
                customerDetails.getRemainingPlafond()
        );
    }
}
