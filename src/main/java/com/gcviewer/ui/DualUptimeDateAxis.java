package com.gcviewer.ui;

import com.gcviewer.model.GcPauseEvent;
import org.jfree.chart.axis.AxisLocation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Heap chart: bottom = JVM uptime (seconds), top = wall-clock from the GC log.
 */
final class DualUptimeDateAxis {
    private DualUptimeDateAxis() {
    }

    static void attach(XYPlot plot, List<GcPauseEvent> events) {
        if (plot == null || events == null || events.isEmpty()) {
            return;
        }
        long anchorMs = ChartAxisConstraints.minLogTimeMillis(events);
        if (anchorMs < 0) {
            return;
        }
        double minUptime = ChartAxisConstraints.minUptime(events);

        if (!(plot.getDomainAxis(0) instanceof NumberAxis uptimeAxis)) {
            return;
        }

        uptimeAxis.setLabel("Uptime (seconds)");
        uptimeAxis.setNumberFormatOverride(new DecimalFormat("0"));

        DateAxis dateAxis = new DateAxis("Time (GC log)");
        dateAxis.setDateFormatOverride(new SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()));
        plot.setDomainAxis(1, dateAxis);
        plot.setDomainAxisLocation(1, AxisLocation.TOP_OR_LEFT);

        SyncState state = new SyncState();
        Runnable sync = () -> syncRange(state, uptimeAxis, dateAxis, anchorMs, minUptime);
        uptimeAxis.addChangeListener(e -> sync.run());
        sync.run();
    }

    private static void syncRange(SyncState state, NumberAxis uptimeAxis, DateAxis dateAxis,
                                  long anchorMs, double minUptime) {
        if (state.syncing) {
            return;
        }
        double lower = uptimeAxis.getLowerBound();
        double upper = uptimeAxis.getUpperBound();
        if (upper <= lower) {
            return;
        }
        long lowerMs = anchorMs + (long) ((lower - minUptime) * 1000.0);
        long upperMs = anchorMs + (long) ((upper - minUptime) * 1000.0);
        if (lowerMs >= upperMs) {
            return;
        }

        long currentLo = dateAxis.getMinimumDate().getTime();
        long currentHi = dateAxis.getMaximumDate().getTime();
        if (currentLo == lowerMs && currentHi == upperMs) {
            return;
        }

        state.syncing = true;
        try {
            dateAxis.setRange(new Date(lowerMs), new Date(upperMs));
        } finally {
            state.syncing = false;
        }
    }

    private static final class SyncState {
        boolean syncing;
    }
}
