package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 片区唯一取水点。每个片区在同一供水方案下只配置一个取水点，
 * 住户去重归属后即获得“唯一取水点”，不存在一户多点。
 * 保留取水时段、无障碍接驳与罐容约束。
 */
@Entity
@Table(name = "water_pickup_point")
public class WaterPickupPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 32)
    private String zoneCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 32)
    private String openTime;

    @Column(nullable = false, length = 32)
    private String closeTime;

    /** 是否提供无障碍接驳（轮椅通道/低位取水/协助送水）。 */
    @Column(nullable = false)
    private boolean accessible;

    /** 取水点接驳车辆的单次罐容上限（升）。 */
    @Column(nullable = false)
    private double tankCapacityLitres;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public WaterPickupPoint() {
    }

    public WaterPickupPoint(String code, String zoneCode, String name,
                            String openTime, String closeTime, boolean accessible,
                            double tankCapacityLitres, OffsetDateTime createdAt) {
        this.code = code;
        this.zoneCode = zoneCode;
        this.name = name;
        this.openTime = openTime;
        this.closeTime = closeTime;
        this.accessible = accessible;
        this.tankCapacityLitres = tankCapacityLitres;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public String getName() {
        return name;
    }

    public String getOpenTime() {
        return openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public double getTankCapacityLitres() {
        return tankCapacityLitres;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
