package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 临时供水居民通知。片区恢复时旧通知不删除，而是置 SUPERSEDED 并新发撤回通知
 * （WITHDRAW 且 replacesNotificationId 链回旧通知）；新增片区/配额变化发 UPDATE。
 * 已恢复区域的待配送通知不得继续显示为有效。
 */
@Entity
@Table(name = "supply_notification")
public class SupplyNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long planId;

    @Column(nullable = false, length = 32)
    private String zoneCode;

    /** 住户稳定标识；设施/片区广播通知可为空。 */
    @Column(length = 64)
    private String residentCode;

    /** ALLOCATION（确定可取量）/ UPDATE（配额或取水点变更）/ WITHDRAW（恢复撤回）。 */
    @Column(nullable = false, length = 16)
    private String kind;

    /** SENT / SUPERSEDED */
    @Column(nullable = false, length = 16)
    private String status = "SENT";

    @Column(nullable = false, length = 1024)
    private String content;

    private Long replacesNotificationId;

    @Column(nullable = false)
    private OffsetDateTime sentAt;

    public SupplyNotification() {
    }

    public SupplyNotification(Long planId, String zoneCode, String residentCode, String kind,
                              String content, Long replacesNotificationId, OffsetDateTime sentAt) {
        this.planId = planId;
        this.zoneCode = zoneCode;
        this.residentCode = residentCode;
        this.kind = kind;
        this.content = content;
        this.replacesNotificationId = replacesNotificationId;
        this.sentAt = sentAt;
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

    public String getResidentCode() {
        return residentCode;
    }

    public String getKind() {
        return kind;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getContent() {
        return content;
    }

    public Long getReplacesNotificationId() {
        return replacesNotificationId;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }
}
