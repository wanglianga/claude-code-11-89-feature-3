package com.waterrepair.supply;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时供水只读聚合：方案台账、住户去重名册、水源与凭证视图，供回放与调度核对。
 */
@Service
public class SupplyQueryService {

    private final SupplyPlanRepository planRepo;
    private final ZoneQuotaRepository quotaRepo;
    private final QuotaLedgerEventRepository quotaLedgerRepo;
    private final WaterSourceRepository sourceRepo;
    private final WaterSourceLedgerRepository sourceLedgerRepo;
    private final SupplyZoneRepository zoneRepo;
    private final WaterPickupPointRepository pickupRepo;
    private final ResidentRegistrationRepository residentRepo;
    private final WaterDeliveryRepository deliveryRepo;
    private final WaterSignoffRepository signoffRepo;
    private final WaterReturnRepository returnRepo;
    private final SupplyNotificationRepository notificationRepo;
    private final ResidentDedupService dedupService;

    public SupplyQueryService(SupplyPlanRepository planRepo,
                              ZoneQuotaRepository quotaRepo,
                              QuotaLedgerEventRepository quotaLedgerRepo,
                              WaterSourceRepository sourceRepo,
                              WaterSourceLedgerRepository sourceLedgerRepo,
                              SupplyZoneRepository zoneRepo,
                              WaterPickupPointRepository pickupRepo,
                              ResidentRegistrationRepository residentRepo,
                              WaterDeliveryRepository deliveryRepo,
                              WaterSignoffRepository signoffRepo,
                              WaterReturnRepository returnRepo,
                              SupplyNotificationRepository notificationRepo,
                              ResidentDedupService dedupService) {
        this.planRepo = planRepo;
        this.quotaRepo = quotaRepo;
        this.quotaLedgerRepo = quotaLedgerRepo;
        this.sourceRepo = sourceRepo;
        this.sourceLedgerRepo = sourceLedgerRepo;
        this.zoneRepo = zoneRepo;
        this.pickupRepo = pickupRepo;
        this.residentRepo = residentRepo;
        this.deliveryRepo = deliveryRepo;
        this.signoffRepo = signoffRepo;
        this.returnRepo = returnRepo;
        this.notificationRepo = notificationRepo;
        this.dedupService = dedupService;
    }

    public record PlanDetail(SupplyPlan plan,
                             Map<String, Object> summary,
                             List<ZoneQuota> quotas,
                             List<QuotaLedgerEvent> quotaLedger,
                             List<WaterDelivery> deliveries,
                             List<SupplyNotification> notifications) {
    }

    @Transactional(readOnly = true)
    public PlanDetail planDetail(Long planId) {
        SupplyPlan plan = planRepo.findById(planId)
                .orElseThrow(() -> new com.waterrepair.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "PLAN_NOT_FOUND", "供水方案不存在: " + planId));
        List<WaterDelivery> deliveries = deliveryRepo.findByPlanIdOrderByIdAsc(planId);
        Map<String, Object> summary = new LinkedHashMap<>();
        double transferable = ResidentDedupService.round1(quotaRepo.findByPlanIdOrderByZoneCodeAsc(planId)
                .stream().mapToDouble(ZoneQuota::getTransferableLitres).sum());
        summary.put("planCode", plan.getCode());
        summary.put("mode", plan.getMode());
        summary.put("status", plan.getStatus());
        summary.put("qualifiedTotalLitres", plan.getQualifiedTotalLitres());
        summary.put("sumMinimumLitres", plan.getSumMinimumLitres());
        summary.put("sharedPoolLitres", plan.getSharedPoolLitres());
        summary.put("transferableLitres", transferable);
        summary.put("freeLitres", ResidentDedupService.round1(
                plan.getSharedPoolLitres() + transferable));
        return new PlanDetail(plan, summary,
                quotaRepo.findByPlanIdOrderByZoneCodeAsc(planId),
                quotaLedgerRepo.findByPlanIdOrderByIdAsc(planId),
                deliveries,
                notificationRepo.findByPlanIdOrderByIdAsc(planId));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> residentRoster() {
        Map<String, ResidentDedupService.ZoneDedup> summary = dedupService.summarizeActiveZones();
        int reportedTotal = summary.values().stream().mapToInt(ResidentDedupService.ZoneDedup::reportedResidents).sum();
        int uniqueTotal = summary.values().stream().mapToInt(ResidentDedupService.ZoneDedup::uniqueResidents).sum();
        int overlapTotal = summary.values().stream().mapToInt(ResidentDedupService.ZoneDedup::overlapResidents).sum();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportedResidentTotal", reportedTotal);
        m.put("uniqueResidentTotal", uniqueTotal);
        m.put("overlapResidentTotal", overlapTotal);
        m.put("zones", new java.util.ArrayList<>(summary.values()));
        m.put("residents", residentRepo.findAllByOrderByIdAsc());
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> sources() {
        return sourceRepo.findAll().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", s.getCode());
            m.put("name", s.getName());
            m.put("totalCapacityLitres", s.getTotalCapacityLitres());
            m.put("availableLitres", s.getAvailableLitres());
            m.put("qualityCertNo", s.getQualityCertNo());
            m.put("qualityExpiresAt", s.getQualityExpiresAt());
            m.put("qualityValid", s.isQualityValidAt(java.time.OffsetDateTime.now()));
            m.put("status", s.getStatus());
            return m;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<WaterSourceLedger> sourceLedger(String sourceCode) {
        return sourceCode == null ? sourceLedgerRepo.findAllByOrderByIdAsc()
                : sourceLedgerRepo.findBySourceCodeOrderByIdAsc(sourceCode);
    }

    @Transactional(readOnly = true)
    public List<SupplyZone> zones() {
        return zoneRepo.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public List<WaterPickupPoint> pickupPoints() {
        return pickupRepo.findAllByOrderByCodeAsc();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> deliveryDetail(Long deliveryId) {
        WaterDelivery d = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> new com.waterrepair.common.ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "DELIVERY_NOT_FOUND", "派送单不存在: " + deliveryId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("delivery", d);
        m.put("signoffs", signoffRepo.findByDeliveryIdOrderByIdAsc(deliveryId));
        m.put("returns", returnRepo.findByDeliveryIdOrderByIdAsc(deliveryId));
        return m;
    }
}
