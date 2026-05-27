package com.gcviewer.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record GcMetrics(
        double totalRuntimeSeconds,
        double totalGcPauseMs,
        double minGcPauseMs,
        double maxGcPauseMs,
        double avgGcPauseMs,
        double medianGcPauseMs,
        double throughputPercent,
        double gcEventsPerMinute,
        int gcEventCount,
        long totalHeapReclaimedBytes,
        long peakHeapUsedBytes,
        long peakMetaspaceUsedKb,
        double totalConcurrentCycleMs,
        Map<String, Double> pauseMsByReason,
        Map<String, Double> pauseMsByType,
        Map<String, Integer> gcCountByType
) {
    public static GcMetrics empty() {
        return new GcMetrics(
                0, 0, 0, 0, 0, 0, 100, 0, 0, 0, 0, 0, 0,
                Map.of(), Map.of(), Map.of());
    }

    public Map<String, Double> pauseMsByReasonOrdered() {
        return sortByValueDesc(pauseMsByReason);
    }

    public Map<String, Double> pauseMsByTypeOrdered() {
        return sortByValueDesc(pauseMsByType);
    }

    private static Map<String, Double> sortByValueDesc(Map<String, Double> source) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }
}
