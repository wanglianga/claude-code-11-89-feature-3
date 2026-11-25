package com.waterrepair.supply;

import com.waterrepair.auth.AuthService;
import com.waterrepair.auth.Roles;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "07-临时供水调度（去重/双路线配额/签收退回）")
@RestController
@RequestMapping("/api/supply")
public class SupplyController {

    private final SupplyPlanService planService;
    private final WaterDeliveryService deliveryService;
    private final SupplyQueryService queryService;
    private final OutageLinkService outageLinkService;

    public SupplyController(SupplyPlanService planService,
                            WaterDeliveryService deliveryService,
                            SupplyQueryService queryService,
                            OutageLinkService outageLinkService) {
        this.planService = planService;
        this.deliveryService = deliveryService;
        this.queryService = queryService;
        this.outageLinkService = outageLinkService;
    }

    // ---------------- 基础数据与去重名册 ----------------

    @Operation(summary = "供水片区列表（含恢复状态、人均量与设施最低保障量）")
    @GetMapping("/zones")
    public List<SupplyZone> zones() {
        return queryService.zones();
    }

    @Operation(summary = "取水点列表（取水时段、无障碍接驳、罐容）")
    @GetMapping("/pickup-points")
    public List<WaterPickupPoint> pickupPoints() {
        return queryService.pickupPoints();
    }

    @Operation(summary = "住户名册与去重口径：街道上报人数、唯一服务人数、重叠人数与各片区最低量")
    @GetMapping("/residents")
    public Map<String, Object> residents() {
        return queryService.residentRoster();
    }

    @Operation(summary = "读取主任务 ACTIVE 隔离方案确认的停水地址集合，映射到供水片区与唯一取水点")
    @GetMapping("/outage-links")
    public OutageLinkService.OutageLinkResult outageLinks() {
        return outageLinkService.currentOutageLinks();
    }

    @Operation(summary = "合格水源列表（余量、水质凭证号与有效期、当前是否有效）")
    @GetMapping("/sources")
    public List<Map<String, Object>> sources() {
        return queryService.sources();
    }

    @Operation(summary = "水源余量台账（OUT 出库 / RETURN 回补流水）")
    @GetMapping("/source-ledger")
    public List<WaterSourceLedger> sourceLedger(@RequestParam(required = false) String sourceCode) {
        return queryService.sourceLedger(sourceCode);
    }

    // ---------------- 方案与配额 ----------------

    @Operation(summary = "建立供水方案：ZONE_RESERVED（按区锁最低+额外）或 SHARED_POOL（只锁最低，余留共享池）")
    @Roles({"DISPATCHER"})
    @PostMapping("/plans")
    public SupplyPlan createPlan(@Valid @RequestBody SupplyDtos.CreatePlanRequest request,
                                 HttpServletRequest http) {
        return planService.createPlan(request.mode(), request.extraLitresByZone(), operator(http));
    }

    @Operation(summary = "供水方案列表")
    @GetMapping("/plans")
    public List<SupplyPlan> plans() {
        return planService.listPlans();
    }

    @Operation(summary = "方案聚合详情：配额行、配额台账、派送单、通知链")
    @GetMapping("/plans/{id}")
    public SupplyQueryService.PlanDetail planDetail(@PathVariable Long id) {
        return queryService.planDetail(id);
    }

    @Operation(summary = "当前 ACTIVE 方案汇总（共享池、可转移量、自由水量、已签收）")
    @GetMapping("/plans/active/summary")
    public Map<String, Object> activeSummary() {
        return planService.planSummary(planService.activePlan());
    }

    @Operation(summary = "片区恢复回执：撤回待发派送与旧通知，按路线释放/转移未发出配额，已签收保留")
    @Roles({"DISPATCHER"})
    @PostMapping("/zones/{zoneCode}/restore")
    public SupplyZone restore(@PathVariable String zoneCode,
                              @RequestBody(required = false) SupplyDtos.RestoreZoneRequest request) {
        return planService.restoreZone(zoneCode, request == null ? null : request.note());
    }

    @Operation(summary = "重新启用已恢复片区（新增独立片区纳入滚动调度前使用）")
    @Roles({"DISPATCHER"})
    @PostMapping("/zones/{zoneCode}/activate")
    public SupplyZone activate(@PathVariable String zoneCode,
                               @RequestBody(required = false) SupplyDtos.ActivateZoneRequest request) {
        return planService.activateZone(zoneCode, request == null ? null : request.note());
    }

    @Operation(summary = "路线A：把已恢复片区的可转移配额转给有效片区")
    @Roles({"DISPATCHER"})
    @PostMapping("/quotas/transfer")
    public QuotaLedgerEvent transfer(@RequestBody Map<String, Object> body) {
        return planService.transferQuota((String) body.get("fromZone"),
                (String) body.get("toZone"),
                ((Number) body.get("litres")).doubleValue(),
                (String) body.get("note"));
    }

    @Operation(summary = "路线A：把恢复片区剩余可转移量释放进共享池（litres 为空表示全部释放）")
    @Roles({"DISPATCHER"})
    @PostMapping("/quotas/release-transferable")
    public QuotaLedgerEvent releaseTransferable(@RequestBody Map<String, Object> body) {
        Double litres = body.get("litres") == null ? null
                : ((Number) body.get("litres")).doubleValue();
        return planService.releaseTransferableToPool((String) body.get("zoneCode"),
                litres, (String) body.get("note"));
    }

    @Operation(summary = "路线B/通用：从共享池向有效片区滚动分配（护栏保证其余片区最低量不被先到者耗尽）")
    @Roles({"DISPATCHER"})
    @PostMapping("/shared-allocations")
    public QuotaLedgerEvent sharedAlloc(@Valid @RequestBody SupplyDtos.SharedAllocRequest request) {
        return planService.allocateShared(request.zoneCode(), request.litres(), request.note());
    }

    @Operation(summary = "路线A：在最低量之外从共享池追加锁定片区额外配额")
    @Roles({"DISPATCHER"})
    @PostMapping("/extra-reservations")
    public QuotaLedgerEvent reserveExtra(@Valid @RequestBody SupplyDtos.ReserveExtraRequest request) {
        return planService.reserveExtra(request.zoneCode(), request.litres(), request.note());
    }

    // ---------------- 现场执行 ----------------

    @Operation(summary = "装车派送：校验罐容与片区配额，锁定水源并扣减合格余量，写 OUT 台账")
    @Roles({"DISPATCHER"})
    @PostMapping("/deliveries")
    public WaterDelivery load(@Valid @RequestBody SupplyDtos.DeliverRequest request) {
        SupplyPlan plan = planService.activePlan();
        return deliveryService.load(plan.getId(), request, null);
    }

    @Operation(summary = "派送单详情：签收记录与退回复核记录")
    @GetMapping("/deliveries/{id}")
    public Map<String, Object> delivery(@PathVariable Long id) {
        return queryService.deliveryDetail(id);
    }

    @Operation(summary = "现场签收：计入实交量，签收水不返库；住户按唯一服务归属校验")
    @Roles({"DISPATCHER", "CREW"})
    @PostMapping("/deliveries/{id}/signoffs")
    public WaterSignoff signoff(@PathVariable Long id,
                                @Valid @RequestBody SupplyDtos.SignoffRequest request) {
        return deliveryService.signoff(id, request);
    }

    @Operation(summary = "登记未开封退回（已开封拒绝）；挂 PENDING 等待复核")
    @Roles({"DISPATCHER", "CREW"})
    @PostMapping("/deliveries/{id}/returns")
    public WaterReturn createReturn(@PathVariable Long id,
                                    @Valid @RequestBody SupplyDtos.ReturnRequest request) {
        return deliveryService.createReturn(id, request);
    }

    @Operation(summary = "退回复核：APPROVED 且凭证仍有效才回补水源可再分配，REJECTED 不回补")
    @Roles({"DISPATCHER"})
    @PostMapping("/returns/{id}/review")
    public WaterReturn reviewReturn(@PathVariable Long id,
                                    @Valid @RequestBody SupplyDtos.ReturnReviewRequest request,
                                    HttpServletRequest http) {
        return deliveryService.reviewReturn(id, request.approved(), request.note(), operator(http));
    }

    private String operator(HttpServletRequest http) {
        return (String) http.getAttribute(AuthService.ATTR_USERNAME);
    }
}
