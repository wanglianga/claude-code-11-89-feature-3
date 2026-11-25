package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplyZoneRepository extends JpaRepository<SupplyZone, String> {
    List<SupplyZone> findAllByOrderByCodeAsc();

    List<SupplyZone> findByActiveTrueOrderByCodeAsc();

    Optional<SupplyZone> findByCode(String code);
}
