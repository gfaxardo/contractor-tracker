package com.yego.contractortracker.repository;

import com.yego.contractortracker.entity.YangoTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface YangoTransactionRepository extends JpaRepository<YangoTransaction, Long> {
    List<YangoTransaction> findByIsMatchedFalse();
    
    List<YangoTransaction> findByScoutId(String scoutId);
    
    List<YangoTransaction> findByDriverId(String driverId);
    
    List<YangoTransaction> findByMilestoneType(Integer milestoneType);
    
    @Query("SELECT t FROM YangoTransaction t WHERE t.transactionDate = :transactionDate " +
           "AND t.scoutId = :scoutId AND t.comment = :comment AND t.milestoneType = :milestoneType")
    Optional<YangoTransaction> findByUniqueFields(
        @Param("transactionDate") LocalDateTime transactionDate,
        @Param("scoutId") String scoutId,
        @Param("comment") String comment,
        @Param("milestoneType") Integer milestoneType
    );
}

