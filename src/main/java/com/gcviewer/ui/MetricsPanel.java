package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcMetrics;
import com.gcviewer.model.HeapInfo;
import com.gcviewer.util.LogFileFormat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.Map;

public class MetricsPanel extends JPanel {
    private static final DecimalFormat DF = new DecimalFormat("#,##0.##");

    private final JPanel grid = new JPanel(new GridLayout(0, 4, 10, 6));

    public MetricsPanel() {
        super(new BorderLayout());
        setBorder(new EmptyBorder(8, 12, 8, 12));
        grid.setBorder(new EmptyBorder(4, 0, 4, 0));
        add(new JScrollPane(grid, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
    }

    public void update(GcLogModel model) {
        grid.removeAll();
        HeapInfo heap = model.getHeapInfo();
        GcMetrics m = model.getMetrics();

        if (model.getSourcePath() != null) {
            addRow("File", model.getSourcePath().getFileName().toString());
            addRow("Path", model.getSourcePath().toAbsolutePath().toString());
            if (model.getFileSizeBytes() >= 0) {
                addRow("File size", LogFileFormat.formatSize(model.getFileSizeBytes()));
            }
            if (model.getFileLastModified() != null) {
                addRow("Last modified", LogFileFormat.formatModified(model.getFileLastModified()));
            }
        }
        addRow("Lines parsed", String.valueOf(model.getLinesParsed()));

        if (heap != null) {
            addRow("Collector", heap.gcType() != null ? heap.gcType() : "G1");
            if (heap.heapCapacityBytes() > 0) {
                addRow("Heap capacity", DF.format(heap.heapCapacityMb()) + " MB");
                addRow("Region size", DF.format(heap.regionSizeMb()) + " MB");
            }
            if (heap.heapAddress() != null) {
                addRow("Heap address", heap.heapAddress());
            }
            if (heap.compressedOopsMode() != null) {
                addRow("Compressed oops", heap.compressedOopsMode());
            }
        }

        addRow("GC events", String.valueOf(m.gcEventCount()));
        addRow("Analyzed window", DF.format(m.totalRuntimeSeconds()) + " s");
        addRow("GC frequency", DF.format(m.gcEventsPerMinute()) + " / min");
        addRow("Throughput", DF.format(m.throughputPercent()) + "%");

        addRow("Total GC pause", DF.format(m.totalGcPauseMs()) + " ms");
        addRow("Avg pause", DF.format(m.avgGcPauseMs()) + " ms");
        addRow("Median pause", DF.format(m.medianGcPauseMs()) + " ms");
        addRow("Min / Max pause", DF.format(m.minGcPauseMs()) + " / " + DF.format(m.maxGcPauseMs()) + " ms");

        addRow("Heap reclaimed (sum)", ChartFactoryUtil.formatMb(m.totalHeapReclaimedBytes()));
        addRow("Peak heap (before GC)", ChartFactoryUtil.formatMb(m.peakHeapUsedBytes()));
        if (m.peakMetaspaceUsedKb() > 0) {
            addRow("Peak metaspace", DF.format(m.peakMetaspaceUsedKb() / 1024.0) + " MB");
        }
        if (m.totalConcurrentCycleMs() > 0) {
            addRow("Concurrent cycles", DF.format(m.totalConcurrentCycleMs()) + " ms");
        }

        for (Map.Entry<String, Integer> e : m.gcCountByType().entrySet()) {
            addRow("Count: " + e.getKey(), String.valueOf(e.getValue()));
        }

        grid.revalidate();
        grid.repaint();
    }

    private void addRow(String label, String value) {
        grid.add(cell(label + ":", true));
        grid.add(cell(value, false));
    }

    private static JLabel cell(String text, boolean label) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(label ? Font.BOLD : Font.PLAIN, 12f));
        return lbl;
    }
}
