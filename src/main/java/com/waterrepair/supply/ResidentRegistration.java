package com.waterrepair.supply;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 住户登记：以稳定住户标识 residentCode 唯一识别一户，跨街道重复上报不产生重复计量。
 * primaryZoneCode 是按主要住址确定的唯一服务归属；reportedZoneCodes 记录该户被哪些街道上报，
 * 用于解释“上报人数”与“唯一服务人数”的差异。
 */
@Entity
@Table(name = "resident_registration")
public class ResidentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 稳定住户标识（身份证/户号派生），全局唯一，重复上报按此去重。 */
    @Column(unique = true, nullable = false, length = 64)
    private String residentCode;

    @Column(nullable = false, length = 128)
    private String householdName;

    /** 唯一服务归属片区（主要住址所在片区）。 */
    @Column(nullable = false, length = 32)
    private String primaryZoneCode;

    /** 该户实际人数（上报人数按此累加）。 */
    @Column(nullable = false)
    private int headcount;

    /** 所有上报过该户的街道片区（含重复片区）。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "resident_reported_zone",
            joinColumns = @JoinColumn(name = "resident_id"))
    @OrderColumn(name = "idx")
    @Column(name = "zone_code", length = 32)
    private List<String> reportedZoneCodes = new ArrayList<>();

    /** 是否需要无障碍接驳。 */
    @Column(nullable = false)
    private boolean barrierFreeNeeded;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public ResidentRegistration() {
    }

    public ResidentRegistration(String residentCode, String householdName, String primaryZoneCode,
                                int headcount, List<String> reportedZoneCodes,
                                boolean barrierFreeNeeded, OffsetDateTime createdAt) {
        this.residentCode = residentCode;
        this.householdName = householdName;
        this.primaryZoneCode = primaryZoneCode;
        this.headcount = headcount;
        if (reportedZoneCodes != null) {
            this.reportedZoneCodes = new ArrayList<>(reportedZoneCodes);
        }
        this.barrierFreeNeeded = barrierFreeNeeded;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getResidentCode() {
        return residentCode;
    }

    public String getHouseholdName() {
        return householdName;
    }

    public String getPrimaryZoneCode() {
        return primaryZoneCode;
    }

    public int getHeadcount() {
        return headcount;
    }

    public List<String> getReportedZoneCodes() {
        return reportedZoneCodes;
    }

    public boolean isBarrierFreeNeeded() {
        return barrierFreeNeeded;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
