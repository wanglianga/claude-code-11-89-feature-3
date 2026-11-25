package com.waterrepair.supply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.OffsetDateTime;
import java.util.Map;

/** 临时供水调度接口请求 DTO。 */
public final class SupplyDtos {

    private SupplyDtos() {
    }

    /** mode=ZONE_RESERVED 时可用 extraLitresByZone 指定各区额外配额；SHARED_POOL 忽略该字段。 */
    public record CreatePlanRequest(@NotBlank String mode,
                                    Map<String, Double> extraLitresByZone) {
    }

    public record RestoreZoneRequest(String note) {
    }

    public record ActivateZoneRequest(String note) {
    }

    public record SharedAllocRequest(@NotBlank String zoneCode,
                                     @NotNull @Positive Double litres,
                                     String note) {
    }

    public record ReserveExtraRequest(@NotBlank String zoneCode,
                                      @NotNull @Positive Double litres,
                                      String note) {
    }

    public record DeliverRequest(@NotBlank String zoneCode,
                                 @NotBlank String vehicleCode,
                                 @NotBlank String sourceCode,
                                 @NotNull @Positive Double litres,
                                 String note) {
    }

    public record SignoffRequest(@NotNull @Positive Double litres,
                                 String residentCode,
                                 String recipientType,
                                 @NotBlank String signer,
                                 String note,
                                 OffsetDateTime signedAt) {
    }

    public record ReturnRequest(@NotNull @Positive Double litres,
                                @NotNull Boolean sealed,
                                String note,
                                OffsetDateTime returnedAt) {
    }

    public record ReturnReviewRequest(@NotNull Boolean approved,
                                      String note) {
    }
}
