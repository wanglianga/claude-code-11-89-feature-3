package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplyNotificationRepository extends JpaRepository<SupplyNotification, Long> {
    List<SupplyNotification> findByPlanIdOrderByIdAsc(Long planId);

    List<SupplyNotification> findByPlanIdAndZoneCodeOrderByIdAsc(Long planId, String zoneCode);
}
