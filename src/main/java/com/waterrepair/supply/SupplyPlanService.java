package com.waterrepair.supply;

import com.waterrepair.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 临时供水方案与配额台账。
 *
 * 两条路线：
 * - ZONE_RESERVED：派车前按片区锁定最低量与额外配额，居民更早得到确定可取量；
 *   片区恢复后未发出的预留成为可转移量，可转给新片区或释放进共享池。
 * - SHARED_POOL：只锁各片区最低保障量，其余合格水留在共享池，凭恢复回执与新需求滚动派送。
 *
 * 不变量：任何时刻仍有效片区的最低保障量必须可被满足（共享池分配受未覆盖最低量护栏约束）；
 * 已签收水量永久保留，恢复只释放未发出的水；所有变化写配额台账事件，旧通知不删除只作废。
 */
@Service
public class SupplyPlanService {

    public static final String ZONE_RESERVED = "ZONE_RESERVED";
    public static final String SHARED_POOL = "SHARED_POOL";

    private final SupplyPlanRepository planRepo;
    private final ZoneQuotaRepository quotaRepo;
    private final QuotaLedgerEventRepository ledgerRepo;
    private final SupplyZoneRepository zoneRepo;
    private final WaterSourceRepository sourceRepo;
    private final WaterPickupPointRepository pickupRepo;
    private final SupplyNotificationRepository notificationRepo;
    private final WaterDeliveryRepository deliveryRepo;
    private final WaterDeliveryService deliveryService;
    private final ResidentDedupService dedupService;

    public SupplyPlanService(SupplyPlanRepository planRepo,
                             ZoneQuotaRepository quotaRepo,
                             QuotaLedgerEventRepository ledgerRepo,
                             SupplyZoneRepository zoneRepo,
                             WaterSourceRepository sourceRepo,
                             WaterPickupPointRepository pickupRepo,
                             SupplyNotificationRepository notificationRepo,
                             WaterDeliveryRepository deliveryRepo,
                             WaterDeliveryService deliveryService,
                             ResidentDedupService dedupService) {
        this.planRepo = planRepo;
        this.quotaRepo = quotaRepo;
        this.ledgerRepo = ledgerRepo;
        this.zoneRepo = zoneRepo;
        this.sourceRepo = sourceRepo;
        this.pickupRepo = pickupRepo;
        this.notificationRepo = notificationRepo;
        this.deliveryRepo = deliveryRepo;
        this.deliveryService = deliveryService;
        this.dedupService = dedupService;
    }

    // ---------------- 方案建立 ----------------

    @Transactional
    public SupplyPlan createPlan(String mode, Map<String, Double> extraByZone, String operator) {
        if (!ZONE_RESERVED.equals(mode) && !SHARED_POOL.equals(mode)) {
            throw ApiException.badRequest("UNKNOWN_SUPPLY_MODE", "mode 必须是 ZONE_RESERVED 或 SHARED_POOL");
        }
        planRepo.findFirstByStatusOrderByIdDesc("ACTIVE").ifPresent(p -> {
            throw ApiException.conflict("PLAN_ALREADY_ACTIVE",
                    "已存在 ACTIVE 供水方案 " + p.getCode() + "，请先在其基础上滚动调整或归档",
                    Map.of("planCode", p.getCode()));
        });

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, ResidentDedupService.ZoneDedup> summary = dedupService.summarizeActiveZones();
        if (summary.isEmpty()) {
            throw ApiException.unprocessable("NO_ACTIVE_ZONE", "当前没有有效供水片区，无法建立方案", null);
        }
        double qualifiedTotal = qualifiedSourceTotal(now);
        double sumMinimum = ResidentDedupService.round1(
                summary.values().stream().mapToDouble(ResidentDedupService.ZoneDedup::minimumLitres).sum());
        if (qualifiedTotal + 1e-6 < sumMinimum) {
            throw ApiException.unprocessable("SUPPLY_INSUFFICIENT",
                    "合格水源余量 " + qualifiedTotal + " 升不足以覆盖各片区最低保障合计 " + sumMinimum + " 升",
                    Map.of("qualifiedTotalLitres", qualifiedTotal, "sumMinimumLitres", sumMinimum));
        }

        Map<String, Double> extras = extraByZone == null ? Map.of() : extraByZone;
        double sumExtra = ZONE_RESERVED.equals(mode)
                ? ResidentDedupService.round1(
                        extras.values().stream().mapToDouble(Double::doubleValue).sum())
                : 0.0;
        if (ZONE_RESERVED.equals(mode) && qualifiedTotal + 1e-6 < sumMinimum + sumExtra) {
            throw ApiException.unprocessable("SUPPLY_INSUFFICIENT",
                    "合格水源不足以同时覆盖最低保障与额外配额",
                    Map.of("qualifiedTotalLitres", qualifiedTotal,
                            "sumMinimumLitres", sumMinimum, "sumExtraLitres", sumExtra));
        }

        SupplyPlan plan = new SupplyPlan("SP-" + System.currentTimeMillis(), mode,
                new ArrayList<>(summary.keySet()), qualifiedTotal, sumMinimum,
                ResidentDedupService.round1(qualifiedTotal - sumMinimum - sumExtra),
                operator, now);
        plan = planRepo.saveAndFlush(plan);
        plan.setCode(String.format("SP-%04d", plan.getId()));
        plan = planRepo.save(plan);

        double pool = plan.getSharedPoolLitres();
        for (ResidentDedupService.ZoneDedup z : summary.values()) {
            ZoneQuota q = new ZoneQuota();
            q.setPlanId(plan.getId());
            q.setZoneCode(z.zoneCode());
            q.setUniqueResidents(z.uniqueResidents());
            q.setReportedResidents(z.reportedResidents());
            q.setResidentMinimumLitres(z.residentMinimumLitres());
            q.setFacilityMinimumLitres(z.facilityMinimumLitres());
            q.setMinimumLitres(z.minimumLitres());
            double extra = ZONE_RESERVED.equals(mode)
                    ? ResidentDedupService.round1(extras.getOrDefault(z.zoneCode(), 0.0))
                    : 0.0;
            q.setReservedLitres(ResidentDedupService.round1(z.minimumLitres() + extra));
            q.setDispatchedLitres(0);
            q.setDeliveredLitres(0);
            q.setTransferableLitres(0);
            q.setStatus("ACTIVE");
            quotaRepo.save(q);

            ledgerRepo.save(new QuotaLedgerEvent(plan.getId(), z.zoneCode(), "LOCK_MINIMUM",
                    z.minimumLitres(), 0, q.getReservedLitres(), pool, null,
                    "按唯一服务归属锁定片区最低保障量", now));
            if (extra > 0) {
                pool = ResidentDedupService.round1(pool - extra);
                ledgerRepo.save(new QuotaLedgerEvent(plan.getId(), z.zoneCode(), "LOCK_EXTRA",
                        extra, -extra, q.getReservedLitres(), pool, null,
                        "派车前额外配额随最低量一并锁定", now));
            }
            notifyAllocation(plan, q, now);
        }
        return plan;
    }

    /** 合格水源总量：凭证在指定时刻仍有效者的可用余量之和。过期凭证的库存不计入、不能补足。 */
    @Transactional(readOnly = true)
    public double qualifiedSourceTotal(OffsetDateTime moment) {
        return ResidentDedupService.round1(sourceRepo.findAll().stream()
                .filter(s -> s.isQualityValidAt(moment))
                .mapToDouble(WaterSource::getAvailableLitres)
                .sum());
    }

    private void notifyAllocation(SupplyPlan plan, ZoneQuota q, OffsetDateTime now) {
        WaterPickupPoint point = pickupRepo.findByZoneCode(q.getZoneCode()).orElse(null);
        String content = String.format(
                "【临时供水安排】%s 已为您所在片区确定取水点 %s（%s-%s，无障碍接驳：%s），"
                        + "本片区确定可取水量 %s 升（唯一服务 %d 人，去重前街道上报 %d 人）。",
                plan.getCode(),
                point == null ? "待定" : point.getCode(),
                point == null ? "--:--" : point.getOpenTime(),
                point == null ? "--:--" : point.getCloseTime(),
                point != null && point.isAccessible() ? "有" : "无",
                trim(q.getReservedLitres()), q.getUniqueResidents(), q.getReportedResidents());
        notificationRepo.save(new SupplyNotification(plan.getId(), q.getZoneCode(), null,
                "ALLOCATION", content, null, now));
    }

    // ---------------- 片区恢复 / 新增 ----------------

    @Transactional
    public SupplyZone restoreZone(String zoneCode, String note) {
        SupplyPlan plan = activePlan();
        SupplyZone zone = mustZone(zoneCode);
        if (!zone.isActive()) {
            throw ApiException.conflict("ZONE_ALREADY_RESTORED",
                    "片区 " + zoneCode + " 已恢复，不能重复恢复", null);
        }
        OffsetDateTime now = OffsetDateTime.now();
        zone.setActive(false);
        zone.setRestoredAt(now);
        zone.setRestoreNote(note);
        zoneRepo.save(zone);

        ZoneQuota q = quotaRepo.findForUpdate(plan.getId(), zoneCode)
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND",
                        "片区 " + zoneCode + " 在当前方案中没有配额行"));

        // 统一加锁顺序“配额 → 派送单 → 水源”：先持配额锁，再撤回在途车（内部锁车与水源）。
        for (WaterDelivery d : deliveryRepo.findByPlanIdAndZoneCodeOrderByIdAsc(plan.getId(), zoneCode)) {
            if (("LOADED".equals(d.getStatus()) || "PARTIALLY_DELIVERED".equals(d.getStatus()))
                    && d.getSignedLitres() == 0 && d.outstandingLitres() > 0) {
                deliveryService.cancelForRestore(d, note == null ? "片区恢复" : note);
            }
        }

        double signed = q.getDeliveredLitres();
        // 未签收的预留全部释放（含已撤回车辆回补到水源的部分，其物理库存由水源台账回补）；
        // 已部分签收的在途车继续按原派送单服务，后续未开封退回仍走复核流程，dispatched 不手工篡改。
        double unallocated = ResidentDedupService.round1(q.getReservedLitres() - signed);
        q.setReservedLitres(0);
        q.setStatus("RELEASED");

        List<SupplyNotification> old = notificationRepo
                .findByPlanIdAndZoneCodeOrderByIdAsc(plan.getId(), zoneCode).stream()
                .filter(n -> "SENT".equals(n.getStatus())).toList();
        if (ZONE_RESERVED.equals(plan.getMode())) {
            // 路线 A：未发出的预留成为该片区的可转移量，不直接进共享池。
            q.setTransferableLitres(unallocated);
            ledgerRepo.save(new QuotaLedgerEvent(plan.getId(), zoneCode, "RESTORE_RELEASE",
                    -unallocated, 0, 0, plan.getSharedPoolLitres(), null,
                    "片区恢复回执：未发出预留转为可转移量，已签收 " + trim(signed) + " 升保留", now));
        } else {
            // 路线 B：只锁最低量，恢复后未发出的最低保障量回到共享池滚动派送。
            double pool = ResidentDedupService.round1(plan.getSharedPoolLitres() + unallocated);
            plan.setSharedPoolLitres(pool);
            planRepo.save(plan);
            ledgerRepo.save(new QuotaLedgerEvent(plan.getId(), zoneCode, "SHARED_RELEASE",
                    -unallocated, unallocated, 0, pool, null,
                    "片区恢复回执：最低保障量释放回共享池，已签收 " + trim(signed) + " 升保留", now));
        }
        quotaRepo.save(q);
        withdrawNotifications(plan, zoneCode, old, now, note);
        return zone;
    }

    @Transactional
    public SupplyZone activateZone(String zoneCode, String note) {
        SupplyPlan plan = activePlan();
        SupplyZone zone = mustZone(zoneCode);
        if (zone.isActive()) {
            throw ApiException.conflict("ZONE_ALREADY_ACTIVE",
                    "片区 " + zoneCode + " 已在服务中", null);
        }
        zone.setActive(true);
        zone.setRestoredAt(null);
        zone.setRestoreNote(null);
        zoneRepo.save(zone);

        Map<String, ResidentDedupService.ZoneDedup> summary = dedupService.summarizeActiveZones();
        ResidentDedupService.ZoneDedup z = summary.get(zoneCode);
        if (z == null) {
            throw ApiException.unprocessable("ZONE_WITHOUT_RESIDENTS",
                    "片区 " + zoneCode + " 没有登记住户，无法纳入供水方案", null);
        }
        if (quotaRepo.findByPlanIdAndZoneCode(plan.getId(), zoneCode).isPresent()) {
            ZoneQuota existing = quotaRepo.findForUpdate(plan.getId(), zoneCode).orElseThrow();
            if ("RELEASED".equals(existing.getStatus())) {
                existing.setStatus("ACTIVE");
                existing.setTransferableLitres(0);
                quotaRepo.save(existing);
            }
            if (!plan.getZoneCodes().contains(zoneCode)) {
                plan.getZoneCodes().add(zoneCode);
                planRepo.save(plan);
            }
            return zone;
        }
        ZoneQuota q = new ZoneQuota();
        q.setPlanId(plan.getId());
        q.setZoneCode(zoneCode);
        q.setUniqueResidents(z.uniqueResidents());
        q.setReportedResidents(z.reportedResidents());
        q.setResidentMinimumLitres(z.residentMinimumLitres());
        q.setFacilityMinimumLitres(z.facilityMinimumLitres());
        q.setMinimumLitres(z.minimumLitres());
        q.setReservedLitres(0);
        q.setDispatchedLitres(0);
        q.setDeliveredLitres(0);
        q.setTransferableLitres(0);
        q.setStatus("ACTIVE");
        quotaRepo.save(q);
        if (!plan.getZoneCodes().contains(zoneCode)) {
            plan.getZoneCodes().add(zoneCode);
            planRepo.save(plan);
        }
        return zone;
    }

    private void withdrawNotifications(SupplyPlan plan, String zoneCode,
                                       List<SupplyNotification> old, OffsetDateTime now, String note) {
        for (SupplyNotification n : old) {
            n.setStatus("SUPERSEDED");
            notificationRepo.save(n);
            SupplyNotification withdraw = new SupplyNotification(plan.getId(), zoneCode,
                    n.getResidentCode(), "WITHDRAW",
                    "【供水通知撤回】片区 " + zoneCode + " 已恢复正常供水，原通知（#" + n.getId()
                            + "）确定可取量不再配送。" + (note == null ? "" : "原因：" + note),
                    n.getId(), now);
            notificationRepo.save(withdraw);
        }
    }

    // ---------------- 路线 A：恢复量转移与释放 ----------------

    @Transactional
    public QuotaLedgerEvent transferQuota(String fromZone, String toZone, double litres, String note) {
        SupplyPlan plan = activePlan();
        if (!ZONE_RESERVED.equals(plan.getMode())) {
            throw ApiException.unprocessable("MODE_MISMATCH",
                    "配额转移仅适用于 ZONE_RESERVED 路线；SHARED_POOL 请使用共享池分配", null);
        }
        OffsetDateTime now = OffsetDateTime.now();
        ZoneQuota from = quotaRepo.findForUpdate(plan.getId(), fromZone)
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND", "源片区不存在: " + fromZone));
        ZoneQuota to = quotaRepo.findForUpdate(plan.getId(), toZone)
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND", "目标片区不存在: " + toZone));
        if (!"RELEASED".equals(from.getStatus()) || from.getTransferableLitres() + 1e-6 < litres) {
            throw ApiException.unprocessable("TRANSFERABLE_INSUFFICIENT",
                    "恢复片区 " + fromZone + " 可转移量不足",
                    Map.of("transferableLitres", from.getTransferableLitres(), "requestedLitres", litres));
        }
        if (!"ACTIVE".equals(to.getStatus())) {
            throw ApiException.unprocessable("TARGET_ZONE_NOT_ACTIVE",
                    "目标片区 " + toZone + " 不是有效服务片区", null);
        }
        from.setTransferableLitres(ResidentDedupService.round1(from.getTransferableLitres() - litres));
        to.setReservedLitres(ResidentDedupService.round1(to.getReservedLitres() + litres));
        quotaRepo.saveAll(List.of(from, to));

        QuotaLedgerEvent event = new QuotaLedgerEvent(plan.getId(), toZone, "RESTORE_TRANSFER",
                litres, 0, to.getReservedLitres(), plan.getSharedPoolLitres(), fromZone,
                "从已恢复片区 " + fromZone + " 转移配额" + (note == null ? "" : "：" + note), now);
        ledgerRepo.save(event);
        notifyQuotaUpdate(plan, to, "收到恢复片区 " + fromZone + " 转入 " + trim(litres) + " 升", now);
        return event;
    }

    @Transactional
    public QuotaLedgerEvent releaseTransferableToPool(String zoneCode, Double litres, String note) {
        SupplyPlan plan = activePlan();
        ZoneQuota q = quotaRepo.findForUpdate(plan.getId(), zoneCode)
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND", "片区不存在: " + zoneCode));
        double amount = litres == null ? q.getTransferableLitres() : litres;
        if (amount <= 0 || q.getTransferableLitres() + 1e-6 < amount) {
            throw ApiException.unprocessable("TRANSFERABLE_INSUFFICIENT",
                    "可转移量不足或为零",
                    Map.of("transferableLitres", q.getTransferableLitres(), "requestedLitres", amount));
        }
        OffsetDateTime now = OffsetDateTime.now();
        q.setTransferableLitres(ResidentDedupService.round1(q.getTransferableLitres() - amount));
        double pool = ResidentDedupService.round1(plan.getSharedPoolLitres() + amount);
        plan.setSharedPoolLitres(pool);
        planRepo.save(plan);
        quotaRepo.save(q);
        QuotaLedgerEvent event = new QuotaLedgerEvent(plan.getId(), zoneCode, "RESTORE_RELEASE",
                -amount, amount, 0, pool, null,
                "恢复片区剩余可转移量释放进共享池" + (note == null ? "" : "：" + note), now);
        return ledgerRepo.save(event);
    }

    // ---------------- 路线 B / 通用：共享池滚动分配、额外配额 ----------------

    @Transactional
    public QuotaLedgerEvent allocateShared(String zoneCode, double litres, String note) {
        SupplyPlan plan = activePlan();
        ZoneQuota q = quotaRepo.findForUpdate(plan.getId(), zoneCode)
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND", "片区不存在: " + zoneCode));
        if (!"ACTIVE".equals(q.getStatus())) {
            throw ApiException.unprocessable("TARGET_ZONE_NOT_ACTIVE",
                    "片区 " + zoneCode + " 已恢复，不能再分配共享水量", null);
        }
        double uncovered = uncoveredMinimumSum(plan.getId(), zoneCode);
        double availableForRequest = ResidentDedupService.round1(plan.getSharedPoolLitres() - uncovered);
        if (litres <= 0 || availableForRequest + 1e-6 < litres) {
            throw ApiException.unprocessable("SHARED_POOL_INSUFFICIENT",
                    "共享池余量不足以在保留其他有效片区最低量后分配该水量",
                    Map.of("sharedPoolLitres", plan.getSharedPoolLitres(),
                            "uncoveredMinimumLitres", uncovered,
                            "availableForRequestLitres", Math.max(0, availableForRequest),
                            "requestedLitres", litres));
        }
        OffsetDateTime now = OffsetDateTime.now();
        double pool = ResidentDedupService.round1(plan.getSharedPoolLitres() - litres);
        plan.setSharedPoolLitres(pool);
        planRepo.save(plan);
        q.setReservedLitres(ResidentDedupService.round1(q.getReservedLitres() + litres));
        quotaRepo.save(q);
        QuotaLedgerEvent event = new QuotaLedgerEvent(plan.getId(), zoneCode, "SHARED_ALLOC",
                litres, -litres, q.getReservedLitres(), pool, null,
                "共享池滚动派送分配" + (note == null ? "" : "：" + note), now);
        ledgerRepo.save(event);
        notifyQuotaUpdate(plan, q, "共享池分配 " + trim(litres) + " 升", now);
        return event;
    }

    @Transactional
    public QuotaLedgerEvent reserveExtra(String zoneCode, double litres, String note) {
        SupplyPlan plan = activePlan();
        if (!ZONE_RESERVED.equals(plan.getMode())) {
            throw ApiException.unprocessable("MODE_MISMATCH",
                    "额外配额锁定仅适用于 ZONE_RESERVED 路线", null);
        }
        ZoneQuota q = quotaRepo.findForUpdate(plan.getId(), zoneCode)
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND", "片区不存在: " + zoneCode));
        double uncovered = uncoveredMinimumSum(plan.getId(), zoneCode);
        double availableForRequest = ResidentDedupService.round1(plan.getSharedPoolLitres() - uncovered);
        if (litres <= 0 || availableForRequest + 1e-6 < litres) {
            throw ApiException.unprocessable("SHARED_POOL_INSUFFICIENT",
                    "共享池余量不足以在保留有效片区最低量后锁定额外配额",
                    Map.of("sharedPoolLitres", plan.getSharedPoolLitres(),
                            "uncoveredMinimumLitres", uncovered, "requestedLitres", litres));
        }
        OffsetDateTime now = OffsetDateTime.now();
        double pool = ResidentDedupService.round1(plan.getSharedPoolLitres() - litres);
        plan.setSharedPoolLitres(pool);
        planRepo.save(plan);
        q.setReservedLitres(ResidentDedupService.round1(q.getReservedLitres() + litres));
        quotaRepo.save(q);
        QuotaLedgerEvent event = new QuotaLedgerEvent(plan.getId(), zoneCode, "LOCK_EXTRA",
                litres, -litres, q.getReservedLitres(), pool, null,
                "追加锁定额外配额" + (note == null ? "" : "：" + note), now);
        ledgerRepo.save(event);
        notifyQuotaUpdate(plan, q, "额外配额锁定 " + trim(litres) + " 升", now);
        return event;
    }

    /** 除 excludeZone 外，其他有效片区尚未覆盖的最低保障量之和（共享池护栏）。 */
    private double uncoveredMinimumSum(Long planId, String excludeZone) {
        double sum = 0;
        for (ZoneQuota other : quotaRepo.findByPlanIdOrderByZoneCodeAsc(planId)) {
            if ("ACTIVE".equals(other.getStatus()) && !other.getZoneCode().equals(excludeZone)) {
                sum += Math.max(0, ResidentDedupService.round1(
                        other.getMinimumLitres() - other.getReservedLitres()));
            }
        }
        return ResidentDedupService.round1(sum);
    }

    private void notifyQuotaUpdate(SupplyPlan plan, ZoneQuota q, String change, OffsetDateTime now) {
        WaterPickupPoint point = pickupRepo.findByZoneCode(q.getZoneCode()).orElse(null);
        String content = String.format(
                "【临时供水安排更新】%s：%s；当前确定可取水量 %s 升，取水点 %s（%s-%s）。",
                plan.getCode(), change, trim(q.getReservedLitres()),
                point == null ? "待定" : point.getCode(),
                point == null ? "--:--" : point.getOpenTime(),
                point == null ? "--:--" : point.getCloseTime());
        notificationRepo.save(new SupplyNotification(plan.getId(), q.getZoneCode(), null,
                "UPDATE", content, null, now));
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public SupplyPlan activePlan() {
        return planRepo.findFirstByStatusOrderByIdDesc("ACTIVE")
                .orElseThrow(() -> ApiException.conflict("NO_ACTIVE_PLAN",
                        "当前没有 ACTIVE 供水方案，请先建立方案", null));
    }

    @Transactional(readOnly = true)
    public SupplyPlan mustGetPlan(Long id) {
        return planRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "供水方案不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<SupplyPlan> listPlans() {
        return planRepo.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<ZoneQuota> quotas(Long planId) {
        return quotaRepo.findByPlanIdOrderByZoneCodeAsc(planId);
    }

    @Transactional(readOnly = true)
    public List<QuotaLedgerEvent> ledger(Long planId) {
        return ledgerRepo.findByPlanIdOrderByIdAsc(planId);
    }

    @Transactional(readOnly = true)
    public List<SupplyNotification> notifications(Long planId) {
        return notificationRepo.findByPlanIdOrderByIdAsc(planId);
    }

    /** 方案汇总：合格总量、最低合计、共享池、可转移量与自由水量（池+可转移）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> planSummary(SupplyPlan plan) {
        List<ZoneQuota> qs = quotaRepo.findByPlanIdOrderByZoneCodeAsc(plan.getId());
        double transferable = ResidentDedupService.round1(
                qs.stream().mapToDouble(ZoneQuota::getTransferableLitres).sum());
        double reserved = ResidentDedupService.round1(
                qs.stream().filter(q -> "ACTIVE".equals(q.getStatus()))
                        .mapToDouble(ZoneQuota::getReservedLitres).sum());
        double delivered = ResidentDedupService.round1(
                qs.stream().mapToDouble(ZoneQuota::getDeliveredLitres).sum());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planCode", plan.getCode());
        m.put("mode", plan.getMode());
        m.put("status", plan.getStatus());
        m.put("qualifiedTotalLitres", plan.getQualifiedTotalLitres());
        m.put("sumMinimumLitres", plan.getSumMinimumLitres());
        m.put("activeReservedLitres", reserved);
        m.put("sharedPoolLitres", plan.getSharedPoolLitres());
        m.put("transferableLitres", transferable);
        m.put("freeLitres", ResidentDedupService.round1(plan.getSharedPoolLitres() + transferable));
        m.put("deliveredLitres", delivered);
        return m;
    }

    private SupplyZone mustZone(String code) {
        return zoneRepo.findByCode(code)
                .orElseThrow(() -> ApiException.notFound("ZONE_NOT_FOUND", "供水片区不存在: " + code));
    }

    private static String trim(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
