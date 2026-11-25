package com.waterrepair.supply;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 不依赖 Spring 容器/数据库的住户去重测试，固定题目样例口径：
 * 甲区上报 180（140 独有 + 40 与乙重复，重叠户主要住址在甲），乙区上报 130（90 独有 + 同 40 重复）；
 * 唯一服务归属应为甲 180、乙 90，而不是 310；人均 2 升 + 设施量得到甲 760、乙 780。
 */
class ResidentDedupServiceTest {

    private final ResidentRegistrationRepository residentRepo = mock(ResidentRegistrationRepository.class);
    private final SupplyZoneRepository zoneRepo = mock(SupplyZoneRepository.class);
    private final ResidentDedupService service = new ResidentDedupService(residentRepo, zoneRepo);

    private final OffsetDateTime now = OffsetDateTime.parse("2026-11-26T04:00:00+08:00");

    private ResidentRegistration resident(String code, String primary, int head, List<String> reported) {
        return new ResidentRegistration(code, code + "-户", primary, head, reported, false, now);
    }

    private List<ResidentRegistration> sampleResidents() {
        List<ResidentRegistration> all = new ArrayList<>();
        for (int i = 1; i <= 140; i++) {
            all.add(resident(String.format("R-JIA-ONLY-%03d", i), "Z_JIA", 1, List.of("Z_JIA")));
        }
        for (int i = 1; i <= 40; i++) {
            all.add(resident(String.format("R-OVL-%03d", i), "Z_JIA", 1, List.of("Z_JIA", "Z_YI")));
        }
        for (int i = 1; i <= 90; i++) {
            all.add(resident(String.format("R-YI-ONLY-%03d", i), "Z_YI", 1, List.of("Z_YI")));
        }
        for (int i = 1; i <= 300; i++) {
            all.add(resident(String.format("R-BING-%03d", i), "Z_BING", 1, List.of("Z_BING")));
        }
        return all;
    }

    @Test
    void overlapsCountedOnceByPrimaryResidenceAndInactiveZoneExcluded() {
        SupplyZone jia = new SupplyZone("Z_JIA", "甲区", 2.0, 400.0, "甲区诊所");
        SupplyZone yi = new SupplyZone("Z_YI", "乙区", 2.0, 600.0, "乙区安置点");
        SupplyZone bing = new SupplyZone("Z_BING", "丙区", 2.0, 0.0, null);
        bing.setActive(false);
        when(zoneRepo.findByActiveTrueOrderByCodeAsc()).thenReturn(List.of(jia, yi));
        when(residentRepo.findAllByOrderByIdAsc()).thenReturn(sampleResidents());

        Map<String, ResidentDedupService.ZoneDedup> r = service.summarizeActiveZones();

        assertThat(r).containsOnlyKeys("Z_JIA", "Z_YI");
        ResidentDedupService.ZoneDedup jiaD = r.get("Z_JIA");
        assertThat(jiaD.uniqueResidents()).isEqualTo(180);
        assertThat(jiaD.reportedResidents()).isEqualTo(180);
        assertThat(jiaD.overlapResidents()).isZero();
        assertThat(jiaD.residentMinimumLitres()).isEqualTo(360.0);
        assertThat(jiaD.minimumLitres()).isEqualTo(760.0);

        ResidentDedupService.ZoneDedup yiD = r.get("Z_YI");
        assertThat(yiD.uniqueResidents()).isEqualTo(90);
        assertThat(yiD.reportedResidents()).isEqualTo(130);
        assertThat(yiD.overlapResidents()).isEqualTo(40);
        assertThat(yiD.residentMinimumLitres()).isEqualTo(180.0);
        assertThat(yiD.minimumLitres()).isEqualTo(780.0);

        int uniqueTotal = r.values().stream().mapToInt(ResidentDedupService.ZoneDedup::uniqueResidents).sum();
        int reportedTotal = r.values().stream().mapToInt(ResidentDedupService.ZoneDedup::reportedResidents).sum();
        assertThat(uniqueTotal).isEqualTo(270);
        assertThat(reportedTotal).isEqualTo(310);
    }

    @Test
    void headcountAboveOneAggregatesPerHousehold() {
        SupplyZone jia = new SupplyZone("Z_JIA", "甲区", 2.0, 0.0, null);
        when(zoneRepo.findByActiveTrueOrderByCodeAsc()).thenReturn(List.of(jia));
        when(residentRepo.findAllByOrderByIdAsc()).thenReturn(List.of(
                resident("H1", "Z_JIA", 3, List.of("Z_JIA", "Z_YI")),
                resident("H2", "Z_JIA", 2, List.of("Z_JIA"))));

        Map<String, ResidentDedupService.ZoneDedup> r = service.summarizeActiveZones();
        assertThat(r.get("Z_JIA").uniqueResidents()).isEqualTo(5);
        assertThat(r.get("Z_JIA").minimumLitres()).isEqualTo(10.0);
    }
}
