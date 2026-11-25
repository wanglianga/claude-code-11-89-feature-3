package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterReturnRepository extends JpaRepository<WaterReturn, Long> {
    List<WaterReturn> findByDeliveryIdOrderByIdAsc(Long deliveryId);
}
