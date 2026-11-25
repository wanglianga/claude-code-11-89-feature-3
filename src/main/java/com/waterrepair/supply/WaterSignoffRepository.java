package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterSignoffRepository extends JpaRepository<WaterSignoff, Long> {
    List<WaterSignoff> findByDeliveryIdOrderByIdAsc(Long deliveryId);

    List<WaterSignoff> findByZoneCodeOrderByIdAsc(String zoneCode);
}
