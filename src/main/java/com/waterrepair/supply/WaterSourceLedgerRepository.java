package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WaterSourceLedgerRepository extends JpaRepository<WaterSourceLedger, Long> {
    List<WaterSourceLedger> findBySourceCodeOrderByIdAsc(String sourceCode);

    List<WaterSourceLedger> findAllByOrderByIdAsc();
}
