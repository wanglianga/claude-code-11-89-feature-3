package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 未开封退回复核记录。只有 sealed=true 且复核 APPROVED 的退回才能回补水源并再分配；
 * REJECTED 的退回不回补；已开封（sealed=false）一律不能退回再分配。
 */
@Entity
@Table(name = "water_return")
public class WaterReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deliveryId;

    @Column(nullable = false, length = 32)
    private String sourceCode;

    @Column(nullable = false)
    private double litres;

    /** 退回时是否未开封。 */
    @Column(nullable = false)
    private boolean sealed;

    /** PENDING / APPROVED / REJECTED */
    @Column(nullable = false, length = 16)
    private String reviewStatus = "PENDING";

    @Column(length = 64)
    private String reviewer;

    @Column(length = 512)
    private String reviewNote;

    private OffsetDateTime returnedAt;
    private OffsetDateTime reviewedAt;

    /** 复核通过回补水源后的台账行 id。 */
    private Long ledgerId;

    public WaterReturn() {
    }

    public WaterReturn(Long deliveryId, String sourceCode, double litres, boolean sealed,
                       OffsetDateTime returnedAt) {
        this.deliveryId = deliveryId;
        this.sourceCode = sourceCode;
        this.litres = litres;
        this.sealed = sealed;
        this.returnedAt = returnedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public double getLitres() {
        return litres;
    }

    public boolean isSealed() {
        return sealed;
    }

    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(String reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public OffsetDateTime getReturnedAt() {
        return returnedAt;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Long getLedgerId() {
        return ledgerId;
    }

    public void setLedgerId(Long ledgerId) {
        this.ledgerId = ledgerId;
    }
}
