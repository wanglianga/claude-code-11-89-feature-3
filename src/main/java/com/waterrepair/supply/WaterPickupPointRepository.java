package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WaterPickupPointRepository extends JpaRepository<WaterPickupPoint, Long> {
    List<WaterPickupPoint> findAllByOrderByCodeAsc();

    Optional<WaterPickupPoint> findByZoneCode(String zoneCode);

    Optional<WaterPickupPoint> findByCode(String code);
}
