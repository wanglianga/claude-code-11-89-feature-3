package com.waterrepair.supply;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WaterSourceRepository extends JpaRepository<WaterSource, Long> {

    Optional<WaterSource> findByCode(String code);

    /** 出库/回补前对水源主行加写锁，串行化并发扣减。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WaterSource s where s.code = :code")
    Optional<WaterSource> findByCodeForUpdate(@Param("code") String code);
}
