package com.waterrepair.supply;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住户去重与唯一服务归属计算。
 * 以稳定住户标识 residentCode 为唯一键：一户无论被多少街道重复上报，只按主要住址
 * primaryZoneCode 归属一个片区、计数一次；reportedZoneCodes 仅用于解释上报口径差异。
 */
@Service
public class ResidentDedupService {

    private final ResidentRegistrationRepository residentRepo;
    private final SupplyZoneRepository zoneRepo;

    public ResidentDedupService(ResidentRegistrationRepository residentRepo,
                                SupplyZoneRepository zoneRepo) {
        this.residentRepo = residentRepo;
        this.zoneRepo = zoneRepo;
    }

    public record ZoneDedup(String zoneCode,
                            int uniqueResidents,
                            int reportedResidents,
                            int overlapResidents,
                            double residentMinimumLitres,
                            double facilityMinimumLitres,
                            double minimumLitres) {
    }

    /** 按片区汇总去重口径；只统计仍有效（未恢复）片区。 */
    @Transactional(readOnly = true)
    public Map<String, ZoneDedup> summarizeActiveZones() {
        List<SupplyZone> zones = zoneRepo.findByActiveTrueOrderByCodeAsc();
        Map<String, int[]> counts = new LinkedHashMap<>(); // [unique, reported]
        for (SupplyZone z : zones) {
            counts.put(z.getCode(), new int[2]);
        }
        for (ResidentRegistration r : residentRepo.findAllByOrderByIdAsc()) {
            int[] bucket = counts.get(r.getPrimaryZoneCode());
            if (bucket != null) {
                bucket[0] += r.getHeadcount();
            }
            for (String reported : r.getReportedZoneCodes().stream().distinct().toList()) {
                int[] reportedBucket = counts.get(reported);
                if (reportedBucket != null) {
                    reportedBucket[1] += r.getHeadcount();
                }
            }
        }
        Map<String, ZoneDedup> result = new LinkedHashMap<>();
        for (SupplyZone z : zones) {
            int[] c = counts.get(z.getCode());
            int unique = c[0];
            int reported = c[1];
            double residentMin = round1(unique * z.getResidentQuotaLitres());
            double min = round1(residentMin + z.getFacilityMinimumLitres());
            result.put(z.getCode(), new ZoneDedup(z.getCode(), unique, reported,
                    reported - unique, residentMin, z.getFacilityMinimumLitres(), min));
        }
        return result;
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
