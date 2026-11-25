package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 配额/共享池台账事件。锁定、额外配额、恢复释放、恢复转移、共享池滚动分配、
 * 撤回释放都追加一行，绝不修改或删除历史——用于解释片区数量与配额的每次变化。
 */
@Entity
@Table(name = "quota_ledger_event")
public class QuotaLedgerEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 32)
    private String zoneCode;

    /**
     * LOCK_MINIMUM / LOCK_EXTRA / RESTORE_RELEASE / RESTORE_TRANSFER /
     * SHARED_ALLOC / SHARED_RELEASE / PLAN_ARCHIVE。
     */
    @Column(nullable = false, length = 32)
    private String eventType;

    /** 带符号的片区配额变化（升）。 */
    @Column(nullable = false)
    private double zoneChangeLitres;

    /** 带符号的共享池变化（升）。 */
    @Column(nullable = false)
    private double poolChangeLitres;

    /** 事件后该片区保留量与共享池余量快照。 */
    @Column(nullable = false)
    private double zoneReservedAfterLitres;

    @Column(nullable = false)
    private double poolAfterLitres;

    @Column(length = 128)
    private String relatedZoneCode;

    @Column(length = 512)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public QuotaLedgerEvent() {
    }

    public QuotaLedgerEvent(Long planId, String zoneCode, String eventType,
                            double zoneChangeLitres, double poolChangeLitres,
                            double zoneReservedAfterLitres, double poolAfterLitres,
                            String relatedZoneCode, String reason, OffsetDateTime createdAt) {
        this.planId = planId;
        this.zoneCode = zoneCode;
        this.eventType = eventType;
        this.zoneChangeLitres = zoneChangeLitres;
        this.poolChangeLitres = poolChangeLitres;
        this.zoneReservedAfterLitres = zoneReservedAfterLitres;
        this.poolAfterLitres = poolAfterLitres;
        this.relatedZoneCode = relatedZoneCode;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public String getEventType() {
        return eventType;
    }

    public double getZoneChangeLitres() {
        return zoneChangeLitres;
    }

    public double getPoolChangeLitres() {
        return poolChangeLitres;
    }

    public double getZoneReservedAfterLitres() {
        return zoneReservedAfterLitres;
    }

    public double getPoolAfterLitres() {
        return poolAfterLitres;
    }

    public String getRelatedZoneCode() {
        return relatedZoneCode;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
