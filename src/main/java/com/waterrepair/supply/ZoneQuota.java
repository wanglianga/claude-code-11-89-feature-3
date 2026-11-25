package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 片区在某一供水方案下的配额行。
 * minimumLitres = 唯一服务人数 × 人均量 + 设施最低量；
 * reservedLitres 为派车前锁定的水量（ZONE_RESERVED 下含额外配额，SHARED_POOL 下只含最低量）；
 * deliveredLitres 仅累计已现场签收的实交量；恢复后配额可释放，但 deliveredLitres 不回退。
 */
@Entity
@Table(name = "zone_quota")
public class ZoneQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 32)
    private String zoneCode;

    /** 去重后唯一服务人数。 */
    @Column(nullable = false)
    private int uniqueResidents;

    /** 街道原始上报人数之和（含重复），用于与唯一人数对照。 */
    @Column(nullable = false)
    private int reportedResidents;

    @Column(nullable = false)
    private double residentMinimumLitres;

    @Column(nullable = false)
    private double facilityMinimumLitres;

    @Column(nullable = false)
    private double minimumLitres;

    @Column(nullable = false)
    private double reservedLitres;

    /** 已装车出库但尚未全部签收的在途/待交量。 */
    @Column(nullable = false)
    private double dispatchedLitres;

    /** 已现场签收的实交量（永久累计，不返库）。 */
    @Column(nullable = false)
    private double deliveredLitres;

    /** ACTIVE / RELEASED（片区恢复或方案归档后释放未发出的水）。 */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    /**
     * ZONE_RESERVED 路线下片区恢复后、尚未再分配的原预留量（可转给新片区或释放进共享池）。
     * 已签收量不计入其中。
     */
    @Column(nullable = false)
    private double transferableLitres;

    public ZoneQuota() {
    }

    public Long getId() {
        return id;
    }

    public Long getPlanId() {
        return planId;
    }

    public void setPlanId(Long planId) {
        this.planId = planId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public void setZoneCode(String zoneCode) {
        this.zoneCode = zoneCode;
    }

    public int getUniqueResidents() {
        return uniqueResidents;
    }

    public void setUniqueResidents(int uniqueResidents) {
        this.uniqueResidents = uniqueResidents;
    }

    public int getReportedResidents() {
        return reportedResidents;
    }

    public void setReportedResidents(int reportedResidents) {
        this.reportedResidents = reportedResidents;
    }

    public double getResidentMinimumLitres() {
        return residentMinimumLitres;
    }

    public void setResidentMinimumLitres(double residentMinimumLitres) {
        this.residentMinimumLitres = residentMinimumLitres;
    }

    public double getFacilityMinimumLitres() {
        return facilityMinimumLitres;
    }

    public void setFacilityMinimumLitres(double facilityMinimumLitres) {
        this.facilityMinimumLitres = facilityMinimumLitres;
    }

    public double getMinimumLitres() {
        return minimumLitres;
    }

    public void setMinimumLitres(double minimumLitres) {
        this.minimumLitres = minimumLitres;
    }

    public double getReservedLitres() {
        return reservedLitres;
    }

    public void setReservedLitres(double reservedLitres) {
        this.reservedLitres = reservedLitres;
    }

    public double getDispatchedLitres() {
        return dispatchedLitres;
    }

    public void setDispatchedLitres(double dispatchedLitres) {
        this.dispatchedLitres = dispatchedLitres;
    }

    public double getDeliveredLitres() {
        return deliveredLitres;
    }

    public void setDeliveredLitres(double deliveredLitres) {
        this.deliveredLitres = deliveredLitres;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTransferableLitres() {
        return transferableLitres;
    }

    public void setTransferableLitres(double transferableLitres) {
        this.transferableLitres = transferableLitres;
    }
}
