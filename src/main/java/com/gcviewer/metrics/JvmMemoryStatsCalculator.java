package com.gcviewer.metrics;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcPauseEvent;
import com.gcviewer.model.GenerationMemoryStats;
import com.gcviewer.model.HeapInfo;
import com.gcviewer.model.JvmMemoryStats;

import java.util.List;

public class JvmMemoryStatsCalculator {

    public JvmMemoryStats calculate(GcLogModel model) {
        HeapInfo heap = model.getHeapInfo();
        long regionBytes = heap != null && heap.regionSizeBytes() > 0
                ? heap.regionSizeBytes() : 1024L * 1024L;

        List<GcPauseEvent> events = model.getPauseEvents();
        if (events.isEmpty()) {
            return emptyStats();
        }

        long youngAlloc = 0;
        long youngPeak = 0;
        long oldAlloc = 0;
        long oldPeak = 0;
        long humPeak = 0;
        long metaAlloc = 0;
        long metaPeak = 0;

        for (GcPauseEvent e : events) {
            int edenCap = nz(e.edenRegionsCap());
            int survCap = nz(e.survivorRegionsCap());
            int edenBefore = nz(e.edenRegionsBefore());
            int survBefore = nz(e.survivorRegionsBefore());
            int edenAfter = nz(e.edenRegionsAfter());
            int oldBefore = nz(e.oldRegionsBefore());
            int oldAfter = nz(e.oldRegionsAfter());
            int humAfter = nz(e.humongousRegionsAfter());

            if (edenCap + survCap > 0) {
                youngAlloc = Math.max(youngAlloc, (long) (edenCap + survCap) * regionBytes);
            }
            youngPeak = Math.max(youngPeak, (long) (edenBefore + survBefore) * regionBytes);
            if (edenAfter > 0 && youngPeak == 0) {
                youngPeak = Math.max(youngPeak, (long) edenAfter * regionBytes);
            }

            oldAlloc = Math.max(oldAlloc, (long) Math.max(oldBefore, oldAfter) * regionBytes);
            oldPeak = Math.max(oldPeak, (long) oldAfter * regionBytes);

            humPeak = Math.max(humPeak, (long) humAfter * regionBytes);

            if (e.metaspaceCapacityKb() != null) {
                metaAlloc = Math.max(metaAlloc, e.metaspaceCapacityKb() * 1024L);
            }
            if (e.metaspaceUsedAfterKb() != null) {
                metaPeak = Math.max(metaPeak, e.metaspaceUsedAfterKb() * 1024L);
            }
        }

        if (youngAlloc == 0 && youngPeak > 0) {
            youngAlloc = youngPeak;
        }

        GenerationMemoryStats young = new GenerationMemoryStats("Young Generation", youngAlloc, youngPeak, youngAlloc > 0);
        GenerationMemoryStats old = new GenerationMemoryStats("Old Generation", oldAlloc, oldPeak, oldAlloc > 0);
        GenerationMemoryStats humongous = humPeak > 0
                ? new GenerationMemoryStats("Humongous", 0, humPeak, false)
                : GenerationMemoryStats.unknown("Humongous");
        GenerationMemoryStats metaspace = metaPeak > 0
                ? new GenerationMemoryStats("Meta Space", metaAlloc, metaPeak, metaAlloc > 0)
                : GenerationMemoryStats.unknown("Meta Space");

        long totalAlloc = young.allocatedBytes() + old.allocatedBytes() + metaspace.allocatedBytes();
        long totalPeak = young.peakBytes() + old.peakBytes() + humongous.peakBytes() + metaspace.peakBytes();
        GenerationMemoryStats total = new GenerationMemoryStats(
                "Young + Old + Meta Space",
                totalAlloc,
                totalPeak,
                true
        );

        return new JvmMemoryStats(young, old, humongous, metaspace, total);
    }

    private static int nz(Integer value) {
        return value != null ? value : 0;
    }

    private static JvmMemoryStats emptyStats() {
        return new JvmMemoryStats(
                GenerationMemoryStats.unknown("Young Generation"),
                GenerationMemoryStats.unknown("Old Generation"),
                GenerationMemoryStats.unknown("Humongous"),
                GenerationMemoryStats.unknown("Meta Space"),
                GenerationMemoryStats.unknown("Young + Old + Meta Space")
        );
    }
}
