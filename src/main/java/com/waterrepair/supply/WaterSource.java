package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 合格水源。availableLitres 是当前可分配的合格余量：
 * 装车出库时扣减、未开封退回复核通过后回补；水质凭证到期后只可读、不可再出库或回补。
 */
@Entity
@Table(name = "water_source")
public class WaterSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 入库总量（台账基数，不随出库变化）。 */
    @Column(nullable = false)
    private double totalCapacityLitres;

    /** 当前合格余量。 */
    @Column(nullable = false)
    private double availableLitres;

    @Column(nullable = false, length = 64)
    private String qualityCertNo;

    @Column(nullable = false)
    private OffsetDateTime qualityExpiresAt;

    /** QUALIFIED / EXPIRED / EXHAUSTED，仅作展示；是否可用以凭证时间与余量实时判定。 */
    @Column(nullable = false, length = 16)
    private String status = "QUALIFIED";

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public WaterSource() {
    }

    public WaterSource(String code, String name, double totalCapacityLitres,
                       String qualityCertNo, OffsetDateTime qualityExpiresAt, OffsetDateTime createdAt) {
        this.code = code;
        this.name = name;
        this.totalCapacityLitres = totalCapacityLitres;
        this.availableLitres = totalCapacityLitres;
        this.qualityCertNo = qualityCertNo;
        this.qualityExpiresAt = qualityExpiresAt;
        this.createdAt = createdAt;
    }

    public boolean isQualityValidAt(OffsetDateTime moment) {
        return qualityExpiresAt != null && qualityExpiresAt.isAfter(moment);
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getTotalCapacityLitres() {
        return totalCapacityLitres;
    }

    public double getAvailableLitres() {
        return availableLitres;
    }

    public void setAvailableLitres(double availableLitres) {
        this.availableLitres = availableLitres;
    }

    public String getQualityCertNo() {
        return qualityCertNo;
    }

    public OffsetDateTime getQualityExpiresAt() {
        return qualityExpiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
