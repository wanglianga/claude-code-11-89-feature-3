package com.waterrepair.supply;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZoneQuotaRepository extends JpaRepository<ZoneQuota, Long> {

    List<ZoneQuota> findByPlanIdOrderByZoneCodeAsc(Long planId);

    Optional<ZoneQuota> findByPlanIdAndZoneCode(Long planId, String zoneCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ZoneQuota q where q.planId = :planId and q.zoneCode = :zoneCode")
    Optional<ZoneQuota> findForUpdate(@Param("planId") Long planId, @Param("zoneCode") String zoneCode);
}
