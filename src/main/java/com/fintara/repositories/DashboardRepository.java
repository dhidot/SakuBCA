package com.fintara.repositories;

import com.fintara.models.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardRepository extends JpaRepository<Dashboard, Long> {

    @Query("SELECT COUNT(u) FROM User u")
    long getTotalUsers();

    @Query("SELECT COUNT(r) FROM Role r")
    long getTotalRoles();
}
