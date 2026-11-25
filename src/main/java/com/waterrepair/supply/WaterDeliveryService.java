package com.waterrepair.supply;

import com.waterrepair.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 临时供水现场执行：装车出库、现场签收、未开封退回与复核。
 *
 * 库存规则：
 * - 装车即对水源主行加锁并扣减合格余量，写 OUT 台账；凭证过期或余量不足一律拒绝；
 * - 现场签收只计实交（片区 deliveredLitres 与派送单 signedLitres），签收量永不返库；
 * - 未开封退回先挂 PENDING 复核单，APPROVED 且凭证仍有效时才回补水源（RETURN 台账）可再分配；
 *   已开封退回直接拒绝，复核不通过的退回不回补、水量留在车上。
 */
@Service
public class WaterDeliveryService {

    private final WaterDeliveryRepository deliveryRepo;
    private final WaterSourceRepository sourceRepo;
    private final WaterSourceLedgerRepository sourceLedgerRepo;
    private final WaterVehicleRepository vehicleRepo;
    private final WaterPickupPointRepository pickupRepo;
    private final SupplyZoneRepository zoneRepo;
    private final ZoneQuotaRepository quotaRepo;
    private final WaterSignoffRepository signoffRepo;
    private final WaterReturnRepository returnRepo;
    private final ResidentRegistrationRepository residentRepo;

    public WaterDeliveryService(WaterDeliveryRepository deliveryRepo,
                                WaterSourceRepository sourceRepo,
                                WaterSourceLedgerRepository sourceLedgerRepo,
                                WaterVehicleRepository vehicleRepo,
                                WaterPickupPointRepository pickupRepo,
                                SupplyZoneRepository zoneRepo,
                                ZoneQuotaRepository quotaRepo,
                                WaterSignoffRepository signoffRepo,
                                WaterReturnRepository returnRepo,
                                ResidentRegistrationRepository residentRepo) {
        this.deliveryRepo = deliveryRepo;
        this.sourceRepo = sourceRepo;
        this.sourceLedgerRepo = sourceLedgerRepo;
        this.vehicleRepo = vehicleRepo;
        this.pickupRepo = pickupRepo;
        this.zoneRepo = zoneRepo;
        this.quotaRepo = quotaRepo;
        this.signoffRepo = signoffRepo;
        this.returnRepo = returnRepo;
        this.residentRepo = residentRepo;
    }

    // ---------------- 装车出库 ----------------

    @Transactional
    public WaterDelivery load(Long planId, SupplyDtos.DeliverRequest req, String operator) {
        SupplyZone zone = zoneRepo.findByCode(req.zoneCode())
                .orElseThrow(() -> ApiException.notFound("ZONE_NOT_FOUND", "片区不存在: " + req.zoneCode()));
        if (!zone.isActive()) {
            throw ApiException.unprocessable("ZONE_RESTORED",
                    "片区 " + zone.getCode() + " 已恢复供水，不能再向其派送", null);
        }
        ZoneQuota quota = quotaRepo.findForUpdate(planId, req.zoneCode())
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND",
                        "片区在当前方案中没有配额行"));
        if (!"ACTIVE".equals(quota.getStatus())) {
            throw ApiException.unprocessable("ZONE_QUOTA_RELEASED", "片区配额已释放，不能装车", null);
        }
        double litres = ResidentDedupService.round1(req.litres());
        double committed = ResidentDedupService.round1(
                quota.getDeliveredLitres() + quota.getDispatchedLitres());
        if (committed + litres > quota.getReservedLitres() + 1e-6) {
            throw ApiException.unprocessable("QUOTA_EXCEEDED",
                    "装车量超过片区已锁定配额，剩余可装车 "
                            + ResidentDedupService.round1(quota.getReservedLitres() - committed) + " 升",
                    Map.of("reservedLitres", quota.getReservedLitres(),
                            "committedLitres", committed, "requestedLitres", litres));
        }

        WaterVehicle vehicle = vehicleRepo.findByCodeForUpdate(req.vehicleCode())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_WATER_VEHICLE",
                        "供水车辆不存在: " + req.vehicleCode()));
        if (!vehicle.isAvailable()) {
            throw ApiException.unprocessable("WATER_VEHICLE_UNAVAILABLE",
                    "车辆 " + vehicle.getCode() + " 当前不可用：" + vehicle.getUnavailableReason(), null);
        }
        WaterPickupPoint point = pickupRepo.findByZoneCode(req.zoneCode())
                .orElseThrow(() -> ApiException.unprocessable("PICKUP_POINT_MISSING",
                        "片区尚未配置取水点，无法安排车辆", null));
        double cap = Math.min(vehicle.getTankCapacityLitres(), point.getTankCapacityLitres());
        if (litres > cap + 1e-6) {
            throw ApiException.unprocessable("TANK_CAPACITY_EXCEEDED",
                    "装车量超过车辆/取水点接驳罐容上限 " + cap + " 升",
                    Map.of("vehicleCapacityLitres", vehicle.getTankCapacityLitres(),
                            "pickupCapacityLitres", point.getTankCapacityLitres(),
                            "requestedLitres", litres));
        }

        OffsetDateTime now = OffsetDateTime.now();
        WaterSource source = sourceRepo.findByCodeForUpdate(req.sourceCode())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_WATER_SOURCE",
                        "水源不存在: " + req.sourceCode()));
        if (!source.isQualityValidAt(now)) {
            throw ApiException.unprocessable("SOURCE_CERT_EXPIRED",
                    "水源 " + source.getCode() + " 水质凭证已于 "
                            + source.getQualityExpiresAt() + " 到期，不能出库；过期凭证不能由库存总量补足",
                    Map.of("sourceCode", source.getCode(),
                            "qualityExpiresAt", source.getQualityExpiresAt().toString()));
        }
        if (source.getAvailableLitres() + 1e-6 < litres) {
            throw ApiException.unprocessable("SOURCE_STOCK_INSUFFICIENT",
                    "水源合格余量不足",
                    Map.of("availableLitres", source.getAvailableLitres(), "requestedLitres", litres));
        }

        double balance = ResidentDedupService.round1(source.getAvailableLitres() - litres);
        source.setAvailableLitres(balance);
        if (balance == 0) {
            source.setStatus("EXHAUSTED");
        }
        sourceRepo.save(source);

        WaterDelivery delivery = new WaterDelivery("WD-" + System.currentTimeMillis(), planId,
                zone.getCode(), point.getCode(), vehicle.getCode(), source.getCode(), litres, now);
        delivery = deliveryRepo.saveAndFlush(delivery);
        delivery.setCode(String.format("WD-%04d", delivery.getId()));
        delivery = deliveryRepo.save(delivery);

        sourceLedgerRepo.save(new WaterSourceLedger(source.getCode(), "OUT", -litres,
                balance, delivery.getId(),
                "装车派送 " + delivery.getCode() + " → " + zone.getCode()
                        + (req.note() == null ? "" : "：" + req.note()), now));

        quota.setDispatchedLitres(ResidentDedupService.round1(quota.getDispatchedLitres() + litres));
        quotaRepo.save(quota);
        return delivery;
    }

    // ---------------- 现场签收 ----------------

    @Transactional
    public WaterSignoff signoff(Long deliveryId, SupplyDtos.SignoffRequest req) {
        // 先普通读取确定片区，再按“配额 → 派送单”的统一顺序加锁，避免与装车/恢复事务形成相反锁序。
        WaterDelivery preview = deliveryRepo.findById(deliveryId)
                .orElseThrow(() -> ApiException.notFound("DELIVERY_NOT_FOUND", "派送单不存在: " + deliveryId));
        ZoneQuota quotaLocked = quotaRepo.findForUpdate(preview.getPlanId(), preview.getZoneCode())
                .orElseThrow(() -> ApiException.notFound("ZONE_QUOTA_NOT_FOUND", "片区配额行不存在"));
        WaterDelivery delivery = deliveryRepo.findByIdForUpdate(deliveryId).orElseThrow();
        if (!"LOADED".equals(delivery.getStatus()) && !"PARTIALLY_DELIVERED".equals(delivery.getStatus())) {
            throw ApiException.conflict("DELIVERY_NOT_OPEN",
                    "派送单状态 " + delivery.getStatus() + " 不允许再签收", null);
        }
        double litres = ResidentDedupService.round1(req.litres());
        if (litres <= 0 || litres > delivery.outstandingLitres() + 1e-6) {
            throw ApiException.unprocessable("SIGNOFF_EXCEEDS_LOAD",
                    "签收量超过车上未处理水量 " + delivery.outstandingLitres() + " 升",
                    Map.of("outstandingLitres", delivery.outstandingLitres(), "requestedLitres", litres));
        }
        String recipientType = req.recipientType() == null ? "RESIDENT" : req.recipientType();
        if ("RESIDENT".equals(recipientType)) {
            if (req.residentCode() == null || req.residentCode().isBlank()) {
                throw ApiException.badRequest("RESIDENT_REQUIRED", "住户签收必须提供稳定住户标识");
            }
            ResidentRegistration resident = residentRepo.findByResidentCode(req.residentCode())
                    .orElseThrow(() -> ApiException.badRequest("UNKNOWN_RESIDENT",
                            "住户标识不存在: " + req.residentCode()));
            if (!resident.getPrimaryZoneCode().equals(delivery.getZoneCode())) {
                throw ApiException.unprocessable("RESIDENT_ZONE_MISMATCH",
                        "住户唯一服务归属片区为 " + resident.getPrimaryZoneCode()
                                + "，不能在 " + delivery.getZoneCode() + " 签收",
                        Map.of("residentCode", resident.getResidentCode(),
                                "primaryZoneCode", resident.getPrimaryZoneCode()));
            }
        } else if (!"FACILITY".equals(recipientType)) {
            throw ApiException.badRequest("UNKNOWN_RECIPIENT_TYPE",
                    "recipientType 仅支持 RESIDENT / FACILITY");
        }

        OffsetDateTime now = req.signedAt() == null ? OffsetDateTime.now() : req.signedAt();
        WaterSignoff signoff = new WaterSignoff(deliveryId, delivery.getZoneCode(),
                req.residentCode(), recipientType, litres, req.signer(), now, req.note());
        signoff = signoffRepo.save(signoff);

        delivery.setSignedLitres(ResidentDedupService.round1(delivery.getSignedLitres() + litres));
        quotaLocked.setDispatchedLitres(ResidentDedupService.round1(quotaLocked.getDispatchedLitres() - litres));
        quotaLocked.setDeliveredLitres(ResidentDedupService.round1(quotaLocked.getDeliveredLitres() + litres));
        quotaRepo.save(quotaLocked);

        if (delivery.outstandingLitres() <= 1e-6) {
            delivery.setStatus(delivery.getReturnedLitres() > 0 ? "RETURN_CLOSED" : "DELIVERED");
            delivery.setCompletedAt(now);
        } else {
            delivery.setStatus("PARTIALLY_DELIVERED");
        }
        deliveryRepo.save(delivery);
        return signoff;
    }

    // ---------------- 未开封退回与复核 ----------------

    @Transactional
    public WaterReturn createReturn(Long deliveryId, SupplyDtos.ReturnRequest req) {
        WaterDelivery delivery = deliveryRepo.findByIdForUpdate(deliveryId)
                .orElseThrow(() -> ApiException.notFound("DELIVERY_NOT_FOUND", "派送单不存在: " + deliveryId));
        if ("CANCELLED".equals(delivery.getStatus())) {
            throw ApiException.conflict("DELIVERY_NOT_OPEN", "派送单已撤回，不能登记退回", null);
        }
        if (Boolean.FALSE.equals(req.sealed())) {
            throw ApiException.unprocessable("RETURN_NOT_SEALED",
                    "已开封的水不能退回再分配；只有未开封整箱/整桶允许退回", null);
        }
        double litres = ResidentDedupService.round1(req.litres());
        if (litres <= 0 || litres > delivery.outstandingLitres() + 1e-6) {
            throw ApiException.unprocessable("RETURN_EXCEEDS_LOAD",
                    "退回量超过车上未处理水量 " + delivery.outstandingLitres() + " 升",
                    Map.of("outstandingLitres", delivery.outstandingLitres(), "requestedLitres", litres));
        }
        OffsetDateTime now = req.returnedAt() == null ? OffsetDateTime.now() : req.returnedAt();
        return returnRepo.save(new WaterReturn(deliveryId, delivery.getSourceCode(),
                litres, true, now));
    }

    @Transactional
    public WaterReturn reviewReturn(Long returnId, boolean approved, String note, String reviewer) {
        WaterReturn ret = returnRepo.findById(returnId)
                .orElseThrow(() -> ApiException.notFound("RETURN_NOT_FOUND", "退回记录不存在: " + returnId));
        if (!"PENDING".equals(ret.getReviewStatus())) {
            throw ApiException.conflict("RETURN_ALREADY_REVIEWED",
                    "退回记录已复核：" + ret.getReviewStatus(), null);
        }
        OffsetDateTime now = OffsetDateTime.now();
        ret.setReviewer(reviewer);
        ret.setReviewNote(note);
        ret.setReviewedAt(now);
        if (!approved) {
            ret.setReviewStatus("REJECTED");
            return returnRepo.save(ret);
        }
        return applyApprovedReturn(ret);
    }

    /**
     * 复核通过后的统一回补逻辑（人工复核与片区恢复自动复核共用）。
     * 凭证已过期则拒绝回补——过期水质凭证不能借退回/库存重新进入可分配量。
     */
    @Transactional
    public WaterReturn applyApprovedReturn(WaterReturn ret) {
        OffsetDateTime now = ret.getReviewedAt() == null ? OffsetDateTime.now() : ret.getReviewedAt();
        WaterDelivery preview = deliveryRepo.findById(ret.getDeliveryId()).orElseThrow();
        // 统一加锁顺序：配额 → 派送单 → 水源（恢复事务已持配额锁时为同事务重入，不死锁）。
        ZoneQuota quota = quotaRepo.findForUpdate(preview.getPlanId(), preview.getZoneCode()).orElseThrow();
        WaterDelivery delivery = deliveryRepo.findByIdForUpdate(ret.getDeliveryId()).orElseThrow();
        WaterSource source = sourceRepo.findByCodeForUpdate(ret.getSourceCode())
                .orElseThrow(() -> ApiException.badRequest("UNKNOWN_WATER_SOURCE",
                        "水源不存在: " + ret.getSourceCode()));
        if (!source.isQualityValidAt(now)) {
            throw ApiException.unprocessable("SOURCE_CERT_EXPIRED",
                    "水源 " + source.getCode() + " 水质凭证已过期，退回水不能回补再分配",
                    Map.of("sourceCode", source.getCode(),
                            "qualityExpiresAt", source.getQualityExpiresAt().toString()));
        }
        double balance = ResidentDedupService.round1(source.getAvailableLitres() + ret.getLitres());
        source.setAvailableLitres(balance);
        if ("EXHAUSTED".equals(source.getStatus())) {
            source.setStatus("QUALIFIED");
        }
        sourceRepo.save(source);

        WaterSourceLedger ledger = sourceLedgerRepo.save(new WaterSourceLedger(
                source.getCode(), "RETURN", ret.getLitres(), balance, delivery.getId(),
                "未开封退回复核通过回补：" + (ret.getReviewNote() == null ? "" : ret.getReviewNote()), now));
        ret.setReviewStatus("APPROVED");
        ret.setLedgerId(ledger.getId());
        returnRepo.save(ret);

        delivery.setReturnedLitres(ResidentDedupService.round1(
                delivery.getReturnedLitres() + ret.getLitres()));
        if (delivery.outstandingLitres() <= 1e-6 && !"CANCELLED".equals(delivery.getStatus())) {
            delivery.setStatus("RETURN_CLOSED");
            delivery.setCompletedAt(now);
        }
        deliveryRepo.save(delivery);

        quota.setDispatchedLitres(ResidentDedupService.round1(
                quota.getDispatchedLitres() - ret.getLitres()));
        quotaRepo.save(quota);
        return ret;
    }

    /** 片区恢复时撤回在途派送：车上未处理水按未开封自动复核回补，已签收部分保留。 */
    @Transactional
    public WaterReturn cancelForRestore(WaterDelivery delivery, String note) {
        WaterDelivery locked = deliveryRepo.findByIdForUpdate(delivery.getId()).orElseThrow();
        double outstanding = locked.outstandingLitres();
        OffsetDateTime now = OffsetDateTime.now();
        if (outstanding <= 1e-6) {
            return null;
        }
        locked.setStatus("CANCELLED");
        locked.setCancelReason("片区恢复，派送撤回：" + note);
        locked.setCompletedAt(now);
        deliveryRepo.save(locked);

        WaterReturn ret = new WaterReturn(locked.getId(), locked.getSourceCode(),
                ResidentDedupService.round1(outstanding), true, now);
        ret.setReviewStatus("APPROVED");
        ret.setReviewer("SYSTEM_RESTORE");
        ret.setReviewNote("片区恢复撤回，车上未交水按未开封自动复核回补；已签收 "
                + locked.getSignedLitres() + " 升保留");
        ret.setReviewedAt(now);
        ret = returnRepo.save(ret);
        return applyApprovedReturn(ret);
    }

    @Transactional(readOnly = true)
    public List<WaterDelivery> deliveries(Long planId) {
        return deliveryRepo.findByPlanIdOrderByIdAsc(planId);
    }

    @Transactional(readOnly = true)
    public WaterDelivery mustGetDelivery(Long id) {
        return deliveryRepo.findById(id)
                .orElseThrow(() -> ApiException.notFound("DELIVERY_NOT_FOUND", "派送单不存在: " + id));
    }

    @Transactional(readOnly = true)
    public List<WaterSignoff> signoffs(Long deliveryId) {
        return signoffRepo.findByDeliveryIdOrderByIdAsc(deliveryId);
    }

    @Transactional(readOnly = true)
    public List<WaterReturn> returns(Long deliveryId) {
        return returnRepo.findByDeliveryIdOrderByIdAsc(deliveryId);
    }
}
