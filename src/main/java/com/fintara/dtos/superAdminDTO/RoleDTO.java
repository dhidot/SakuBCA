package com.fintara.dtos.superAdminDTO;

import com.fintara.models.Role;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
public class RoleDTO {
    private UUID id;
    private String name;
    private List<String> features;

    public RoleDTO(Role role) {
        this.id = role.getId();
        this.name = role.getName();
        this.features = role.getRoleFeatures().stream()
                .map(roleFeature -> roleFeature.getFeature().getName()) // Ambil nama dari entitas Feature
                .collect(Collectors.toList());
    }
}