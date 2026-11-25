package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuotaLedgerEventRepository extends JpaRepository<QuotaLedgerEvent, Long> {
    List<QuotaLedgerEvent> findByPlanIdOrderByIdAsc(Long planId);
}
