package com.gcviewer.ui;

import com.gcviewer.model.GcPauseEvent;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.event.PlotChangeListener;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ChartAxisConstraints {
    private static final Map<JFreeChart, PlotChangeListener> LISTENERS = new WeakHashMap<>();

    private ChartAxisConstraints() {
    }

    static void register(JFreeChart chart, List<GcPauseEvent> events) {
        if (chart == null) {
            return;
        }
        unregister(chart);

        long minTime = minLogTimeMillis(events);
        double minUptime = minUptime(events);
        Plot plot = chart.getPlot();

        if (plot instanceof XYPlot xyPlot) {
            applyInitialBounds(xyPlot, minTime, minUptime);
            PlotChangeListener listener = e -> clampXy(xyPlot, minTime, minUptime);
            LISTENERS.put(chart, listener);
            xyPlot.addChangeListener(listener);
        } else if (plot instanceof CategoryPlot categoryPlot) {
            clampCategory(categoryPlot);
            PlotChangeListener listener = e -> clampCategory(categoryPlot);
            LISTENERS.put(chart, listener);
            categoryPlot.addChangeListener(listener);
        }
    }

    private static void unregister(JFreeChart chart) {
        PlotChangeListener existing = LISTENERS.remove(chart);
        if (existing == null) {
            return;
        }
        Plot plot = chart.getPlot();
        if (plot != null) {
            plot.removeChangeListener(existing);
        }
    }

    private static void applyInitialBounds(XYPlot plot, long minTime, double minUptime) {
        clampXy(plot, minTime, minUptime);
        if (plot.getDomainAxis() instanceof DateAxis dateAxis && minTime > 0) {
            dateAxis.setMinimumDate(new Date(minTime));
        }
    }

    private static void clampXy(XYPlot plot, long minTime, double minUptime) {
        if (plot.getRangeAxis() instanceof NumberAxis range) {
            if (range.getLowerBound() < 0) {
                double upper = range.getUpperBound();
                range.setRange(0, Math.max(upper, 1));
            }
        }
        if (plot.getDomainAxis() instanceof NumberAxis domain) {
            if (domain.getLowerBound() < 0) {
                domain.setLowerBound(0);
            }
        }
        if (plot.getDomainAxis() instanceof DateAxis dateAxis && minTime > 0) {
            if (dateAxis.getMinimumDate().getTime() < minTime) {
                dateAxis.setMinimumDate(new Date(minTime));
            }
        }
    }

    private static void clampCategory(CategoryPlot plot) {
        if (plot.getRangeAxis() instanceof NumberAxis range && range.getLowerBound() < 0) {
            range.setLowerBound(0);
        }
    }

    static long minLogTimeMillis(List<GcPauseEvent> events) {
        return events.stream()
                .map(GcPauseEvent::timestamp)
                .filter(ChartAxisConstraints::validTimestamp)
                .mapToLong(Instant::toEpochMilli)
                .min()
                .orElse(-1L);
    }

    static double minUptime(List<GcPauseEvent> events) {
        return events.stream().mapToDouble(GcPauseEvent::uptimeSeconds).min().orElse(0);
    }

    static boolean validTimestamp(Instant ts) {
        return ts != null && !ts.equals(Instant.EPOCH);
    }
}
