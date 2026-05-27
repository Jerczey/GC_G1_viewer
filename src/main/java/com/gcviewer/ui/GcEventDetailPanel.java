package com.gcviewer.ui;

import com.gcviewer.model.GcPauseEvent;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Map;

public class GcEventDetailPanel extends JPanel {
    private static final DecimalFormat DF = new DecimalFormat("#,##0.##");

    private final JTextArea detailArea = new JTextArea();

    public GcEventDetailPanel() {
        super(new BorderLayout());
        setBorder(new TitledBorder("GC Event Details (select a row)"));
        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        add(new JScrollPane(detailArea), BorderLayout.CENTER);
        clear();
    }

    public void showEvent(GcPauseEvent e) {
        if (e == null) {
            clear();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("GC #").append(e.gcId()).append("  |  ").append(e.displayType()).append('\n');
        sb.append("Timestamp: ").append(e.formattedTimestamp())
                .append("  |  Uptime: ").append(DF.format(e.uptimeSeconds())).append(" s\n");
        sb.append("Pause: ").append(DF.format(e.pauseMs())).append(" ms")
                .append("  |  Reclaimed: ").append(ChartFactoryUtil.formatMb(e.heapReclaimedBytes())).append('\n');
        sb.append("Heap: ").append(ChartFactoryUtil.formatMb(e.heapBeforeBytes()))
                .append(" -> ").append(ChartFactoryUtil.formatMb(e.heapAfterBytes()))
                .append(" / ").append(ChartFactoryUtil.formatMb(e.heapCapacityBytes())).append('\n');

        if (e.workers() != null) {
            sb.append("Workers: ").append(e.workers()).append('\n');
        }
        if (e.cpuUserSec() != null) {
            sb.append("CPU: User=").append(DF.format(e.cpuUserSec()))
                    .append("s Sys=").append(DF.format(e.cpuSysSec()))
                    .append("s Real=").append(DF.format(e.cpuRealSec())).append("s\n");
        }
        if (e.concurrentCycleMs() != null) {
            sb.append("Concurrent cycle: ").append(DF.format(e.concurrentCycleMs())).append(" ms\n");
        }

        appendRegion(sb, "Eden", e.edenRegionsBefore(), e.edenRegionsAfter(), e.edenRegionsCap());
        appendRegion(sb, "Survivor", e.survivorRegionsBefore(), e.survivorRegionsAfter(), e.survivorRegionsCap());
        appendRegion(sb, "Old", e.oldRegionsBefore(), e.oldRegionsAfter(), null);
        appendRegion(sb, "Humongous", e.humongousRegionsBefore(), e.humongousRegionsAfter(), null);

        if (e.metaspaceUsedAfterKb() != null) {
            sb.append("Metaspace: ").append(e.metaspaceUsedBeforeKb()).append("K -> ")
                    .append(e.metaspaceUsedAfterKb()).append("K (cap ")
                    .append(e.metaspaceCapacityKb()).append("K)");
            if (e.metaspaceClassUsedKb() != null) {
                sb.append("  Class: ").append(e.metaspaceClassUsedKb()).append("K");
            }
            sb.append('\n');
        }

        if (!e.phaseTimingsMs().isEmpty()) {
            sb.append("Phases:\n");
            for (Map.Entry<String, Double> phase : e.phaseTimingsMsOrdered().entrySet()) {
                sb.append("  - ").append(phase.getKey()).append(": ")
                        .append(DF.format(phase.getValue())).append(" ms\n");
            }
        }

        detailArea.setText(sb.toString());
        detailArea.setCaretPosition(0);
    }

    private static void appendRegion(StringBuilder sb, String name, Integer before, Integer after, Integer cap) {
        if (before == null && after == null) {
            return;
        }
        sb.append(name).append(" regions: ");
        if (before != null) {
            sb.append(before);
        } else {
            sb.append("?");
        }
        sb.append(" -> ");
        if (after != null) {
            sb.append(after);
        } else {
            sb.append("?");
        }
        if (cap != null) {
            sb.append(" (cap ").append(cap).append(')');
        }
        sb.append('\n');
    }

    public void clear() {
        detailArea.setText("Select a GC event in the table to see phases, G1 regions, metaspace, and CPU data.");
    }
}
