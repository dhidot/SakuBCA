package com.sakuBCA.dtos.superAdminDTO;

import com.sakuBCA.models.Feature;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class FeatureDTO {
    private UUID id;
    private String name;

    public FeatureDTO(Feature feature) {
        this.id = feature.getId();
        this.name = feature.getName();
    }
}
