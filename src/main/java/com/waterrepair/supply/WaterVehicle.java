package com.waterrepair.supply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 临时供水罐车。装车量不得超过罐容；available=false 的车辆不能承担新派送。
 */
@Entity
@Table(name = "water_vehicle")
public class WaterVehicle {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 32)
    private String plateNo;

    @Column(nullable = false)
    private double tankCapacityLitres;

    @Column(nullable = false)
    private boolean available = true;

    @Column(length = 128)
    private String unavailableReason;

    public WaterVehicle() {
    }

    public WaterVehicle(String code, String name, String plateNo,
                        double tankCapacityLitres, boolean available, String unavailableReason) {
        this.code = code;
        this.name = name;
        this.plateNo = plateNo;
        this.tankCapacityLitres = tankCapacityLitres;
        this.available = available;
        this.unavailableReason = unavailableReason;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public double getTankCapacityLitres() {
        return tankCapacityLitres;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getUnavailableReason() {
        return unavailableReason;
    }
}
