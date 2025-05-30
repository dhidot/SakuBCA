package com.fintara.repositories;

import com.fintara.models.Feature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureRepository extends JpaRepository<Feature, UUID> {
    Optional<Feature> findByName(String name);

    Optional<Feature> findById(UUID id);

    List<Feature> findAll();

    @Query("SELECT f.name FROM Feature f WHERE f.id = :featureId")
    String findFeatureNameById(@Param("featureId") UUID featureId);

    @Query("SELECT f.id AS id, f.category AS category, f.name AS name FROM Feature f")
    List<FeatureCategoryView> findAllProjected();
}

