package com.waterrepair.supply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResidentRegistrationRepository extends JpaRepository<ResidentRegistration, Long> {
    Optional<ResidentRegistration> findByResidentCode(String residentCode);

    List<ResidentRegistration> findAllByOrderByIdAsc();

    List<ResidentRegistration> findByPrimaryZoneCodeOrderByIdAsc(String primaryZoneCode);
}
