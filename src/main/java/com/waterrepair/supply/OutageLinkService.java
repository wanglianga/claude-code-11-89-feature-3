package com.waterrepair.supply;

import com.waterrepair.incident.IsolationPlan;
import com.waterrepair.incident.IsolationPlanRepository;
import com.waterrepair.network.AddressPoint;
import com.waterrepair.network.AddressPointRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 读取主任务（事故）确认的停水地址集合，并映射到供水片区与唯一取水点。
 * 数据来源是各事故当前 ACTIVE 隔离方案快照的 affectedAddressCodes / outageSegmentCodes：
 * 临时供水不自行估计停水范围，只消费主任务已确认的集合，再按片区支管对齐。
 */
@Service
public class OutageLinkService {

    private final IsolationPlanRepository planRepo;
    private final SupplyZoneRepository zoneRepo;
    private final WaterPickupPointRepository pickupRepo;
    private final AddressPointRepository addressRepo;
    private final ResidentRegistrationRepository residentRepo;

    public OutageLinkService(IsolationPlanRepository planRepo,
                             SupplyZoneRepository zoneRepo,
                             WaterPickupPointRepository pickupRepo,
                             AddressPointRepository addressRepo,
                             ResidentRegistrationRepository residentRepo) {
        this.planRepo = planRepo;
        this.zoneRepo = zoneRepo;
        this.pickupRepo = pickupRepo;
        this.addressRepo = addressRepo;
        this.residentRepo = residentRepo;
    }

    public record ZoneOutage(String zoneCode,
                             String zoneName,
                             String segmentCode,
                             boolean zoneActive,
                             List<String> outageAddressCodes,
                             int affectedAddressCount,
                             WaterPickupPoint pickupPoint,
                             int uniqueResidents,
                             double minimumLitres) {
    }

    public record OutageLinkResult(int activePlanCount,
                                   List<String> outageAddressCodes,
                                   List<ZoneOutage> zones,
                                   List<String> unmappedSegmentCodes) {
    }

    @Transactional(readOnly = true)
    public OutageLinkResult currentOutageLinks() {
        List<IsolationPlan> activePlans = planRepo.findAll().stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .toList();

        Map<String, AddressPoint> addressMap = new LinkedHashMap<>();
        for (AddressPoint a : addressRepo.findAll()) {
            addressMap.put(a.getCode(), a);
        }
        Map<String, SupplyZone> zoneBySegment = new LinkedHashMap<>();
        for (SupplyZone z : zoneRepo.findAllByOrderByCodeAsc()) {
            if (z.getSegmentCode() != null) {
                zoneBySegment.put(z.getSegmentCode(), z);
            }
        }
        Map<String, List<String>> addressesBySegment = new LinkedHashMap<>();
        List<String> allAddresses = new ArrayList<>();
        List<String> unmapped = new ArrayList<>();
        for (IsolationPlan plan : activePlans) {
            for (String code : plan.getAffectedAddressCodes()) {
                if (!allAddresses.contains(code)) {
                    allAddresses.add(code);
                }
                AddressPoint point = addressMap.get(code);
                if (point == null) {
                    continue;
                }
                // 多个 ACTIVE 方案可能覆盖同一支管（如复核后的新方案与相邻事故方案），按地址去重。
                List<String> segAddresses =
                        addressesBySegment.computeIfAbsent(point.getSegmentCode(), k -> new ArrayList<>());
                if (!segAddresses.contains(code)) {
                    segAddresses.add(code);
                }
                if (!zoneBySegment.containsKey(point.getSegmentCode())
                        && !unmapped.contains(point.getSegmentCode())) {
                    unmapped.add(point.getSegmentCode());
                }
            }
        }

        List<ZoneOutage> zoneOutages = new ArrayList<>();
        for (SupplyZone zone : zoneRepo.findAllByOrderByCodeAsc()) {
            if (zone.getSegmentCode() == null) {
                continue;
            }
            List<String> codes = addressesBySegment.getOrDefault(zone.getSegmentCode(), List.of())
                    .stream().sorted().toList();
            if (codes.isEmpty()) {
                continue;
            }
            WaterPickupPoint pickup = pickupRepo.findByZoneCode(zone.getCode()).orElse(null);
            int unique = residentRepo.findByPrimaryZoneCodeOrderByIdAsc(zone.getCode()).stream()
                    .mapToInt(ResidentRegistration::getHeadcount).sum();
            double min = ResidentDedupService.round1(
                    unique * zone.getResidentQuotaLitres() + zone.getFacilityMinimumLitres());
            zoneOutages.add(new ZoneOutage(zone.getCode(), zone.getName(), zone.getSegmentCode(),
                    zone.isActive(), codes, codes.size(), pickup, unique, min));
        }
        return new OutageLinkResult(activePlans.size(), allAddresses.stream().sorted().toList(),
                zoneOutages, unmapped);
    }
}
