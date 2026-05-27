package com.gcviewer.parser;

import com.gcviewer.model.GcPauseEvent;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class GcEventBuilder {
    int gcId;
    double uptimeSeconds;
    Instant timestamp = Instant.EPOCH;

    Integer edenBefore;
    Integer edenAfter;
    Integer edenCap;
    Integer survivorBefore;
    Integer survivorAfter;
    Integer survivorCap;
    Integer oldBefore;
    Integer oldAfter;
    Integer humongousBefore;
    Integer humongousAfter;

    Long metaspaceUsedBeforeKb;
    Long metaspaceUsedAfterKb;
    Long metaspaceCapacityKb;
    Long metaspaceClassUsedKb;

    final Map<String, Double> phaseTimingsMs = new LinkedHashMap<>();
    String workers;
    Double cpuUserSec;
    Double cpuSysSec;
    Double cpuRealSec;
    Double concurrentCycleMs;

    GcPauseEvent toPauseEvent(
            String pauseType,
            String reason,
            long heapBefore,
            long heapAfter,
            long heapCapacity,
            double pauseMs
    ) {
        return new GcPauseEvent(
                gcId,
                uptimeSeconds,
                timestamp,
                pauseType,
                reason,
                heapBefore,
                heapAfter,
                heapCapacity,
                pauseMs,
                edenBefore,
                edenAfter,
                edenCap,
                survivorBefore,
                survivorAfter,
                survivorCap,
                oldBefore,
                oldAfter,
                humongousBefore,
                humongousAfter,
                metaspaceUsedBeforeKb,
                metaspaceUsedAfterKb,
                metaspaceCapacityKb,
                metaspaceClassUsedKb,
                Map.copyOf(phaseTimingsMs),
                workers,
                cpuUserSec,
                cpuSysSec,
                cpuRealSec,
                concurrentCycleMs
        );
    }
}
