package com.waterrepair.supply;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WaterDeliveryRepository extends JpaRepository<WaterDelivery, Long> {
    List<WaterDelivery> findByPlanIdOrderByIdAsc(Long planId);

    List<WaterDelivery> findByPlanIdAndZoneCodeOrderByIdAsc(Long planId, String zoneCode);

    Optional<WaterDelivery> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from WaterDelivery d where d.id = :id")
    Optional<WaterDelivery> findByIdForUpdate(@Param("id") Long id);
}
