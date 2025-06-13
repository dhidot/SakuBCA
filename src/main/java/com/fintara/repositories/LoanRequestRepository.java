package com.fintara.repositories;

import com.fintara.enums.LoanStatus;
import com.fintara.models.CustomerDetails;
import com.fintara.models.LoanRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRequestRepository extends JpaRepository<LoanRequest, UUID> {
    List<LoanRequest> findByCustomer(CustomerDetails customer);

    @Query("SELECT lr.marketing.id, COUNT(lr) FROM LoanRequest lr WHERE lr.branch.id = :branchId GROUP BY lr.marketing.id")
    List<Object[]> countLoanRequestsByMarketing(@Param("branchId") UUID branchId);

    @Query("SELECT lr FROM LoanRequest lr WHERE lr.marketing.id = :marketingId AND lr.status.name = 'REVIEW'")
    List<LoanRequest> findByMarketingId(@Param("marketingId") UUID marketingId);

    @Query("SELECT l FROM LoanRequest l WHERE l.branch.id = :branchId AND l.status.name = :status")
    List<LoanRequest> findByBranchIdAndStatus(@Param("branchId") UUID branchId, @Param("status") String status);

    List<LoanRequest> findAllByCustomer_User_IdAndStatus_NameIn(UUID userId, List<String> statusNames);

    boolean existsByCustomerAndStatus_NameIn(CustomerDetails customer, List<String> statusNames);

    int countByStatusNameAndMarketingId(String statusName, UUID marketingId);

    int countByStatusNameAndBranchId(String statusName, UUID branchId);
}
