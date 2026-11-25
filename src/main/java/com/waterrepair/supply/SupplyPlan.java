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
 * 一次临时供水调度方案。两种路线：
 * ZONE_RESERVED 按片区锁定最低量+额外配额，居民更早得到确定可取量；
 * SHARED_POOL 只锁各片区最低保障量，其余合格水留在共享池滚动派送。
 * 同一时刻只允许一个 ACTIVE 方案；恢复/重算通过配额台账事件留痕，不抹历史。
 */
@Entity
@Table(name = "supply_plan")
public class SupplyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 32)
    private String code;

    /** ZONE_RESERVED / SHARED_POOL */
    @Column(nullable = false, length = 24)
    private String mode;

    /** ACTIVE / ARCHIVED */
    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 方案纳入的片区（建方案时的仍有效片区快照）。 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "supply_plan_zone", joinColumns = @JoinColumn(name = "plan_id"))
    @OrderColumn(name = "idx")
    @Column(name = "zone_code", length = 32)
    private List<String> zoneCodes = new ArrayList<>();

    /** 方案锁定时刻的合格水源总量（只计凭证仍有效者）。 */
    @Column(nullable = false)
    private double qualifiedTotalLitres;

    @Column(nullable = false)
    private double sumMinimumLitres;

    @Column(nullable = false)
    private double sharedPoolLitres;

    @Column(length = 64)
    private String createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    private OffsetDateTime archivedAt;

    public SupplyPlan() {
    }

    public SupplyPlan(String code, String mode, List<String> zoneCodes, double qualifiedTotalLitres,
                      double sumMinimumLitres, double sharedPoolLitres, String createdBy,
                      OffsetDateTime createdAt) {
        this.code = code;
        this.mode = mode;
        this.zoneCodes = zoneCodes == null ? new ArrayList<>() : new ArrayList<>(zoneCodes);
        this.qualifiedTotalLitres = qualifiedTotalLitres;
        this.sumMinimumLitres = sumMinimumLitres;
        this.sharedPoolLitres = sharedPoolLitres;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
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

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getZoneCodes() {
        return zoneCodes;
    }

    public double getQualifiedTotalLitres() {
        return qualifiedTotalLitres;
    }

    public double getSumMinimumLitres() {
        return sumMinimumLitres;
    }

    public void setSumMinimumLitres(double sumMinimumLitres) {
        this.sumMinimumLitres = sumMinimumLitres;
    }

    public double getSharedPoolLitres() {
        return sharedPoolLitres;
    }

    public void setSharedPoolLitres(double sharedPoolLitres) {
        this.sharedPoolLitres = sharedPoolLitres;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }
}
