package com.gcviewer.metrics;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcMetrics;
import com.gcviewer.model.GcPauseEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GcMetricsCalculator {

    public GcMetrics calculate(GcLogModel model) {
        List<GcPauseEvent> events = model.getPauseEvents();
        if (events.isEmpty()) {
            return GcMetrics.empty();
        }

        double totalPauseMs = 0;
        double minPauseMs = Double.MAX_VALUE;
        double maxPauseMs = 0;
        long totalReclaimed = 0;
        long peakHeap = 0;
        long peakMetaspaceKb = 0;
        double totalConcurrentMs = 0;
        Map<String, Double> pauseByReason = new LinkedHashMap<>();
        Map<String, Double> pauseByType = new LinkedHashMap<>();
        Map<String, Integer> countByType = new LinkedHashMap<>();
        List<Double> pauses = new ArrayList<>();

        for (GcPauseEvent event : events) {
            totalPauseMs += event.pauseMs();
            minPauseMs = Math.min(minPauseMs, event.pauseMs());
            maxPauseMs = Math.max(maxPauseMs, event.pauseMs());
            pauses.add(event.pauseMs());
            totalReclaimed += event.heapReclaimedBytes();
            peakHeap = Math.max(peakHeap, event.heapBeforeBytes());
            if (event.metaspaceUsedAfterKb() != null) {
                peakMetaspaceKb = Math.max(peakMetaspaceKb, event.metaspaceUsedAfterKb());
            }
            if (event.concurrentCycleMs() != null) {
                totalConcurrentMs += event.concurrentCycleMs();
            }

            String bucket = bucketReason(event);
            pauseByReason.merge(bucket, event.pauseMs(), Double::sum);
            pauseByType.merge(event.pauseType(), event.pauseMs(), Double::sum);
            countByType.merge(event.pauseType(), 1, Integer::sum);
        }

        double firstUptime = events.get(0).uptimeSeconds();
        double lastUptime = events.get(events.size() - 1).uptimeSeconds();
        double runtimeSeconds = Math.max(lastUptime - firstUptime, 0.001);
        double throughput = Math.max(0, Math.min(100, 100.0 * (1.0 - (totalPauseMs / 1000.0) / runtimeSeconds)));
        double avgPause = totalPauseMs / events.size();
        double median = median(pauses);
        double eventsPerMin = events.size() / (runtimeSeconds / 60.0);

        if (minPauseMs == Double.MAX_VALUE) {
            minPauseMs = 0;
        }

        return new GcMetrics(
                runtimeSeconds,
                totalPauseMs,
                minPauseMs,
                maxPauseMs,
                avgPause,
                median,
                throughput,
                eventsPerMin,
                events.size(),
                totalReclaimed,
                peakHeap,
                peakMetaspaceKb,
                totalConcurrentMs,
                pauseByReason,
                pauseByType,
                countByType
        );
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int mid = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        }
        return sorted.get(mid);
    }

    private static String bucketReason(GcPauseEvent event) {
        String reason = event.shortReason();
        if (reason.contains("Evacuation")) {
            return "G1 Evacuation";
        }
        if (reason.contains("Metadata")) {
            return "Metadata GC Threshold";
        }
        if (reason.contains("GCLocker")) {
            return "GCLocker Initiated";
        }
        if (event.pauseType().contains("Remark")) {
            return "Concurrent Remark";
        }
        if (event.pauseType().contains("Cleanup")) {
            return "Concurrent Cleanup";
        }
        if (event.pauseType().contains("Mixed")) {
            return "Mixed GC";
        }
        if (event.pauseType().contains("Full")) {
            return "Full GC";
        }
        if (event.pauseType().contains("Young")) {
            return "Young GC";
        }
        return reason;
    }
}
