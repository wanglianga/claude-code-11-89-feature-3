package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 水源余量台账：每次装车出库 OUT 与未开封退回回补 RETURN 各留一行，可追溯。
 * 现场签收的水不返库，因此没有任何回补行会来自签收量。
 */
@Entity
@Table(name = "water_source_ledger")
public class WaterSourceLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String sourceCode;

    /** OUT（装车出库，扣减）/ RETURN（未开封退回复核通过，回补）。 */
    @Column(nullable = false, length = 16)
    private String movementType;

    /** 带符号变化量：OUT 为负、RETURN 为正。 */
    @Column(nullable = false)
    private double changeLitres;

    @Column(nullable = false)
    private double balanceAfterLitres;

    /** 关联的派送单（可空，便于手工调整扩展）。 */
    private Long deliveryId;

    @Column(length = 128)
    private String reason;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public WaterSourceLedger() {
    }

    public WaterSourceLedger(String sourceCode, String movementType, double changeLitres,
                             double balanceAfterLitres, Long deliveryId, String reason,
                             OffsetDateTime createdAt) {
        this.sourceCode = sourceCode;
        this.movementType = movementType;
        this.changeLitres = changeLitres;
        this.balanceAfterLitres = balanceAfterLitres;
        this.deliveryId = deliveryId;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getMovementType() {
        return movementType;
    }

    public double getChangeLitres() {
        return changeLitres;
    }

    public double getBalanceAfterLitres() {
        return balanceAfterLitres;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public String getReason() {
        return reason;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
