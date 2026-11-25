package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 现场签收记录：每条都计入片区实交量与派送单 signedLitres，
 * 签收即消耗，不生成任何回库流水；片区恢复也不删除、不冲销。
 */
@Entity
@Table(name = "water_signoff")
public class WaterSignoff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long deliveryId;

    @Column(nullable = false, length = 32)
    private String zoneCode;

    /** 签收住户稳定标识（可空：设施签收时无住户）。 */
    @Column(length = 64)
    private String residentCode;

    /** RESIDENT / FACILITY */
    @Column(nullable = false, length = 16)
    private String recipientType;

    @Column(nullable = false)
    private double litres;

    @Column(nullable = false, length = 64)
    private String signer;

    @Column(nullable = false)
    private OffsetDateTime signedAt;

    @Column(length = 256)
    private String note;

    public WaterSignoff() {
    }

    public WaterSignoff(Long deliveryId, String zoneCode, String residentCode, String recipientType,
                        double litres, String signer, OffsetDateTime signedAt, String note) {
        this.deliveryId = deliveryId;
        this.zoneCode = zoneCode;
        this.residentCode = residentCode;
        this.recipientType = recipientType;
        this.litres = litres;
        this.signer = signer;
        this.signedAt = signedAt;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Long getDeliveryId() {
        return deliveryId;
    }

    public String getZoneCode() {
        return zoneCode;
    }

    public String getResidentCode() {
        return residentCode;
    }

    public String getRecipientType() {
        return recipientType;
    }

    public double getLitres() {
        return litres;
    }

    public String getSigner() {
        return signer;
    }

    public OffsetDateTime getSignedAt() {
        return signedAt;
    }

    public String getNote() {
        return note;
    }
}
