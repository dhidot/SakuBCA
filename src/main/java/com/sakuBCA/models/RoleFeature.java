package com.sakuBCA.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "role_features")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class RoleFeature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "feature_name")
    private String featureName;  // Tambahkan kolom untuk menyimpan nama fitur

    @ManyToOne
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne
    @JoinColumn(name = "feature_id", nullable = false)
    private Feature feature;

    public String getFeatureName() {
        return feature.getName(); // Pastikan Feature memiliki method getName()
    }
}
