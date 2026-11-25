package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 一次送水派送单。装车即从合格水源出库扣减（water_source_ledger 留 OUT 行）；
 * 现场签收累计实交、未开封退回进入复核；签收量永不返库。
 * 片区恢复时，尚无签收的在途单撤回（CANCELLED），已签收部分原样保留。
 */
@Entity
@Table(name = "water_delivery")
public class WaterDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String code;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 32)
    private String zoneCode;

    @Column(nullable = false, length = 32)
    private String pickupPointCode;

    @Column(nullable = false, length = 32)
    private String vehicleCode;

    @Column(nullable = false, length = 32)
    private String sourceCode;

    /** 装车出库量（装车时已从水源扣减）。 */
    @Column(nullable = false)
    private double loadedLitres;

    /** 累计现场签收实交量。 */
    @Column(nullable = false)
    private double signedLitres;

    /** 累计复核通过、已回补水源的未开封退回量。 */
    @Column(nullable = false)
    private double returnedLitres;

    /** LOADED / DELIVERED / PARTIALLY_DELIVERED / RETURN_CLOSED / CANCELLED */
    @Column(nullable = false, length = 24)
    private String status = "LOADED";

    @Column(length = 256)
    private String cancelReason;

    @Column(nullable = false)
    private OffsetDateTime loadedAt;

    private OffsetDateTime completedAt;

    public WaterDelivery() {
    }

    public WaterDelivery(String code, Long planId, String zoneCode, String pickupPointCode,
                         String vehicleCode, String sourceCode, double loadedLitres,
                         OffsetDateTime loadedAt) {
        this.code = code;
        this.planId = planId;
        this.zoneCode = zoneCode;
        this.pickupPointCode = pickupPointCode;
        this.vehicleCode = vehicleCode;
        this.sourceCode = sourceCode;
        this.loadedLitres = loadedLitres;
        this.loadedAt = loadedAt;
    }

    /** 车上尚未处理的水量（未签收且未退回）。 */
    public double outstandingLitres() {
        return loadedLitres - signedLitres - returnedLitres;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Long getPlanId() {
        return planId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public String getPickupPointCode() {
        return pickupPointCode;
    }

    public String getVehicleCode() {
        return vehicleCode;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public double getLoadedLitres() {
        return loadedLitres;
    }

    public double getSignedLitres() {
        return signedLitres;
    }

    public void setSignedLitres(double signedLitres) {
        this.signedLitres = signedLitres;
    }

    public double getReturnedLitres() {
        return returnedLitres;
    }

    public void setReturnedLitres(double returnedLitres) {
        this.returnedLitres = returnedLitres;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public OffsetDateTime getLoadedAt() {
        return loadedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
