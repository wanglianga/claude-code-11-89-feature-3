package com.waterrepair.supply;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplyPlanRepository extends JpaRepository<SupplyPlan, Long> {

    List<SupplyPlan> findAllByOrderByIdAsc();

    Optional<SupplyPlan> findFirstByStatusOrderByIdDesc(String status);

    Optional<SupplyPlan> findByCode(String code);

    /** 建方案前锁方案主表，避免并发建立两个 ACTIVE 方案。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SupplyPlan p where p.id = :id")
    Optional<SupplyPlan> findByIdForUpdate(@Param("id") Long id);
}
