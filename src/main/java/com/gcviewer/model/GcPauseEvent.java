package com.gcviewer.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GcPauseEvent(
        int gcId,
        double uptimeSeconds,
        Instant timestamp,
        String pauseType,
        String reason,
        long heapBeforeBytes,
        long heapAfterBytes,
        long heapCapacityBytes,
        double pauseMs,
        Integer edenRegionsBefore,
        Integer edenRegionsAfter,
        Integer edenRegionsCap,
        Integer survivorRegionsBefore,
        Integer survivorRegionsAfter,
        Integer survivorRegionsCap,
        Integer oldRegionsBefore,
        Integer oldRegionsAfter,
        Integer humongousRegionsBefore,
        Integer humongousRegionsAfter,
        Long metaspaceUsedBeforeKb,
        Long metaspaceUsedAfterKb,
        Long metaspaceCapacityKb,
        Long metaspaceClassUsedKb,
        Map<String, Double> phaseTimingsMs,
        String workers,
        Double cpuUserSec,
        Double cpuSysSec,
        Double cpuRealSec,
        Double concurrentCycleMs
) {
    private static final DateTimeFormatter TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public GcPauseEvent {
        phaseTimingsMs = phaseTimingsMs == null ? Map.of() : Map.copyOf(phaseTimingsMs);
    }

    public String displayType() {
        if (reason != null && !reason.isBlank()) {
            return pauseType + " (" + reason + ")";
        }
        return pauseType;
    }

    public String shortReason() {
        if (reason == null || reason.isBlank()) {
            return pauseType;
        }
        return reason;
    }

    public long heapReclaimedBytes() {
        return Math.max(0, heapBeforeBytes - heapAfterBytes);
    }

    public String formattedTimestamp() {
        return timestamp != null && !timestamp.equals(Instant.EPOCH)
                ? TS.format(timestamp)
                : "-";
    }

    public int regionsUsedAfter() {
        int eden = edenRegionsAfter != null ? edenRegionsAfter : 0;
        int surv = survivorRegionsAfter != null ? survivorRegionsAfter : 0;
        int old = oldRegionsAfter != null ? oldRegionsAfter : 0;
        int hum = humongousRegionsAfter != null ? humongousRegionsAfter : 0;
        return eden + surv + old + hum;
    }

    public Map<String, Double> phaseTimingsMsOrdered() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(phaseTimingsMs));
    }
}
