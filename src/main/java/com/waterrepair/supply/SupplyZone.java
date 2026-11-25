package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 临时供水服务片区（甲/乙/丙…）。active=false 表示片区已恢复供水、不再需要临时供水；
 * 恢复后未发出的配额释放、待发派送撤回、旧通知作废，但已经签收的交付原样保留。
 */
@Entity
@Table(name = "supply_zone")
public class SupplyZone {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 人均最低保障量（升/人）。 */
    @Column(nullable = false)
    private double residentQuotaLitres;

    /** 片区内诊所/安置点等设施的额外最低保障量（升）。 */
    @Column(nullable = false)
    private double facilityMinimumLitres;

    @Column(length = 64)
    private String facilityName;

    /**
     * 片区对应的主任务停水支管（与事故隔离方案的 outageSegmentCodes 对齐）。
     * 新增片区在事故方案建立前可为空。服务据此读取主任务确认的停水地址集合。
     */
    @Column(length = 32)
    private String segmentCode;

    @Column(nullable = false)
    private boolean active = true;

    private OffsetDateTime restoredAt;

    @Column(length = 256)
    private String restoreNote;

    public SupplyZone() {
    }

    public SupplyZone(String code, String name, double residentQuotaLitres,
                      double facilityMinimumLitres, String facilityName) {
        this.code = code;
        this.name = name;
        this.residentQuotaLitres = residentQuotaLitres;
        this.facilityMinimumLitres = facilityMinimumLitres;
        this.facilityName = facilityName;
    }

    public String getSegmentCode() {
        return segmentCode;
    }

    public void setSegmentCode(String segmentCode) {
        this.segmentCode = segmentCode;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public double getResidentQuotaLitres() {
        return residentQuotaLitres;
    }

    public double getFacilityMinimumLitres() {
        return facilityMinimumLitres;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getRestoredAt() {
        return restoredAt;
    }

    public void setRestoredAt(OffsetDateTime restoredAt) {
        this.restoredAt = restoredAt;
    }

    public String getRestoreNote() {
        return restoreNote;
    }

    public void setRestoreNote(String restoreNote) {
        this.restoreNote = restoreNote;
    }
}
