package com.waterrepair.config;

import java.time.OffsetDateTime;
import java.util.List;

/** 随项目提供的 samples/*.json 对应的播种 DTO。 */
public final class SeedDtos {

    private SeedDtos() {
    }

    public record NetworkSeed(List<NodeDto> nodes,
                              List<SegmentDto> segments,
                              List<ValveDto> valves,
                              List<AddressDto> addresses,
                              List<LandmarkDto> landmarks,
                              List<DeviceDto> devices,
                              List<ReadingDto> readings) {
    }

    public record NodeDto(String code, String name, String type, Double lng, Double lat) {
    }

    public record SegmentDto(String code, String name, String from, String to,
                             Double pMin, Double pMax, Integer tolSec) {
    }

    public record ValveDto(String code, String name, String nodeCode,
                           String qualification, String permit) {
    }

    public record AddressDto(String code, String address, String segment, String building,
                             Double lng, Double lat, List<String> aliases) {
    }

    public record LandmarkDto(String code, String name, Double lng, Double lat,
                              List<String> candidateSegments) {
    }

    public record DeviceDto(String deviceNo, String name, String segment) {
    }

    public record ReadingDto(String code, String deviceNo, String segment, Double pressureMpa,
                             OffsetDateTime sampledAt, Integer clockOffsetSeconds, String note) {
    }

    public record CrewsSeed(List<CrewDto> crews,
                            List<VehicleDto> vehicles,
                            List<ToolDto> tools,
                            List<PermitDto> permits) {
    }

    public record CrewDto(String code, String name, String shiftStart, String shiftEnd,
                          String contactPhone, Boolean active, String primaryVehicleCode,
                          List<String> qualificationCodes, List<String> standardToolCodes) {
    }

    public record VehicleDto(String code, String name, String plateNo,
                             Boolean available, String unavailableReason) {
    }

    public record ToolDto(String code, String name, Boolean available, String unavailableReason) {
    }

    public record PermitDto(String code, String name, String valveCode) {
    }

    public record AccountsSeed(List<AccountDto> accounts) {
    }

    public record AccountDto(String username, String password, String displayName,
                             String role, String crewCode) {
    }

    public record SupplySeed(List<SupplyZoneDto> zones,
                             List<PickupPointDto> pickupPoints,
                             List<WaterSourceDto> sources,
                             List<WaterVehicleDto> vehicles,
                             List<ResidentBulkDto> residents) {
    }

    public record SupplyZoneDto(String code, String name, double residentQuotaLitres,
                                double facilityMinimumLitres, String facilityName,
                                String segmentCode, Boolean active) {
    }

    public record PickupPointDto(String code, String zoneCode, String name,
                                 String openTime, String closeTime,
                                 boolean accessible, double tankCapacityLitres) {
    }

    public record WaterSourceDto(String code, String name, double totalCapacityLitres,
                                 String qualityCertNo, OffsetDateTime qualityExpiresAt) {
    }

    public record WaterVehicleDto(String code, String name, String plateNo,
                                  double tankCapacityLitres, Boolean available) {
    }

    /** 批量住户：bulkCount 户共享同一登记口径，逐户生成稳定标识 prefix-001…。 */
    public record ResidentBulkDto(String residentCodePrefix, int bulkCount,
                                  String householdNamePrefix, int headcount,
                                  String primaryZoneCode, List<String> reportedZoneCodes,
                                  boolean barrierFreeNeeded) {
    }
}
