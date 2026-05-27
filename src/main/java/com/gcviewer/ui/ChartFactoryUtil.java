package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcMetrics;
import com.gcviewer.model.GcPauseEvent;
import com.gcviewer.model.HeapSample;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import com.gcviewer.model.JvmMemoryStats;
import com.gcviewer.model.GenerationMemoryStats;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.category.StackedBarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.chart.ui.RectangleInsets;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

public final class ChartFactoryUtil {
    private static final Color USED_COLOR = new Color(52, 120, 198);
    private static final Color CAPACITY_COLOR = new Color(200, 200, 200);

    private ChartFactoryUtil() {
    }

    public static JFreeChart createHeapChart(GcLogModel model) {
        XYSeries used = new XYSeries("Heap Used (MB)");
        XYSeries capacity = new XYSeries("Heap Capacity (MB)");

        List<HeapSample> samples = model.getHeapSamples();
        if (samples.isEmpty()) {
            used.add(0, 0);
            capacity.add(0, model.getHeapInfo() != null ? model.getHeapInfo().heapCapacityMb() : 0);
        } else {
            double lastUptime = -1;
            for (HeapSample sample : samples) {
                double mb = sample.usedBytes() / (1024.0 * 1024.0);
                double capMb = sample.capacityBytes() / (1024.0 * 1024.0);
                used.add(sample.uptimeSeconds(), mb);
                if (sample.uptimeSeconds() != lastUptime) {
                    capacity.add(sample.uptimeSeconds(), capMb);
                    lastUptime = sample.uptimeSeconds();
                }
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(used);
        dataset.addSeries(capacity);

        JFreeChart chart = org.jfree.chart.ChartFactory.createXYLineChart(
                "Heap Usage Over Time",
                "Uptime (seconds)",
                "Memory (MB)",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        plot.setAxisOffset(new RectangleInsets(8, 8, 8, 8));
        plot.setDomainGridlinePaint(new Color(220, 220, 220));
        plot.setRangeGridlinePaint(new Color(220, 220, 220));

        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setSeriesPaint(0, USED_COLOR);
        renderer.setSeriesPaint(1, CAPACITY_COLOR);
        renderer.setSeriesStroke(0, new BasicStroke(2f));
        renderer.setSeriesStroke(1, new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{6f, 4f}, 0f));
        renderer.setSeriesShapesVisible(0, true);
        renderer.setSeriesShapesVisible(1, false);
        plot.setRenderer(renderer);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setLowerBound(0);

        List<GcPauseEvent> events = model.getPauseEvents();
        NumberAxis domainAxis = (NumberAxis) plot.getDomainAxis();
        configureUptimeDomainRange(domainAxis, samples, events);

        DualUptimeDateAxis.attach(plot, events);
        ChartAxisConstraints.register(chart, events);
        return chart;
    }

    private static void configureUptimeDomainRange(NumberAxis domainAxis, List<HeapSample> samples,
                                                   List<GcPauseEvent> events) {
        double dataMax = samples.stream().mapToDouble(HeapSample::uptimeSeconds).max().orElse(1);
        domainAxis.setAutoRange(false);
        domainAxis.setRange(0, Math.max(dataMax, 1));
    }

    public static JFreeChart createPauseBreakdownChart(GcMetrics metrics) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Map<String, Double> ordered = metrics.pauseMsByReasonOrdered();
        if (ordered.isEmpty()) {
            dataset.addValue(0, "Pause", "No GC pauses");
        } else {
            for (Map.Entry<String, Double> entry : ordered.entrySet()) {
                dataset.addValue(entry.getValue(), "Pause Time (ms)", entry.getKey());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "GC Pause Time by Reason",
                "Reason",
                "Accumulated Pause (ms)",
                dataset,
                PlotOrientation.HORIZONTAL,
                false,
                true,
                false
        );

        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new Color(198, 90, 52));
        renderer.setMaximumBarWidth(0.12);
        return chart;
    }

    public static JFreeChart createG1RegionsChart(GcLogModel model, List<GcPauseEvent> events) {
        long regionBytes = model.getHeapInfo() != null ? model.getHeapInfo().regionSizeBytes() : 1024L * 1024L;
        double regionMb = regionBytes / (1024.0 * 1024.0);

        XYSeries eden = new XYSeries("Eden (MB)");
        XYSeries survivor = new XYSeries("Survivor (MB)");
        XYSeries old = new XYSeries("Old (MB)");
        XYSeries humongous = new XYSeries("Humongous (MB)");

        for (GcPauseEvent e : events) {
            if (e.edenRegionsAfter() == null && e.oldRegionsAfter() == null) {
                continue;
            }
            double t = e.uptimeSeconds();
            eden.add(t, (e.edenRegionsAfter() != null ? e.edenRegionsAfter() : 0) * regionMb);
            survivor.add(t, (e.survivorRegionsAfter() != null ? e.survivorRegionsAfter() : 0) * regionMb);
            old.add(t, (e.oldRegionsAfter() != null ? e.oldRegionsAfter() : 0) * regionMb);
            humongous.add(t, (e.humongousRegionsAfter() != null ? e.humongousRegionsAfter() : 0) * regionMb);
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(eden);
        dataset.addSeries(survivor);
        dataset.addSeries(old);
        dataset.addSeries(humongous);

        JFreeChart chart = org.jfree.chart.ChartFactory.createXYLineChart(
                "G1 Regions After GC (estimated MB)",
                "Uptime (seconds)",
                "MB",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        styleMultiSeries(chart.getXYPlot(), new Color[]{new Color(76, 175, 80), new Color(255, 193, 7),
                new Color(244, 67, 54), new Color(156, 39, 176)});
        return chart;
    }

    public static JFreeChart createMetaspaceChart(List<GcPauseEvent> events) {
        XYSeries used = new XYSeries("Metaspace Used (MB)");
        XYSeries capacity = new XYSeries("Metaspace Capacity (MB)");

        for (GcPauseEvent e : events) {
            if (e.metaspaceUsedAfterKb() == null) {
                continue;
            }
            double t = e.uptimeSeconds();
            used.add(t, e.metaspaceUsedAfterKb() / 1024.0);
            if (e.metaspaceCapacityKb() != null) {
                capacity.add(t, e.metaspaceCapacityKb() / 1024.0);
            }
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(used);
        dataset.addSeries(capacity);

        JFreeChart chart = org.jfree.chart.ChartFactory.createXYLineChart(
                "Metaspace After GC",
                "Uptime (seconds)",
                "MB",
                dataset,
                PlotOrientation.VERTICAL,
                true,
                true,
                false
        );
        XYPlot plot = chart.getXYPlot();
        styleMultiSeries(plot, new Color[]{new Color(0, 150, 136), new Color(180, 180, 180)});
        return chart;
    }

    public static JFreeChart createPauseByTypeChart(GcMetrics metrics) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        Map<String, Double> ordered = metrics.pauseMsByTypeOrdered();
        if (ordered.isEmpty()) {
            dataset.addValue(0, "Pause", "None");
        } else {
            for (Map.Entry<String, Double> entry : ordered.entrySet()) {
                dataset.addValue(entry.getValue(), "ms", entry.getKey());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "GC Pause Time by Type",
                "Pause Type",
                "Accumulated (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        ((BarRenderer) plot.getRenderer()).setSeriesPaint(0, new Color(63, 81, 181));
        return chart;
    }

    public static JFreeChart createPhaseChart(GcPauseEvent event) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (event == null || event.phaseTimingsMs().isEmpty()) {
            dataset.addValue(0, "ms", "Select Young GC with phases");
        } else {
            for (Map.Entry<String, Double> phase : event.phaseTimingsMsOrdered().entrySet()) {
                dataset.addValue(phase.getValue(), "ms", phase.getKey());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "GC Phases — GC #" + (event != null ? event.gcId() : "?"),
                "Phase",
                "Time (ms)",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        ((BarRenderer) plot.getRenderer()).setSeriesPaint(0, new Color(0, 121, 107));
        return chart;
    }

    private static void styleMultiSeries(XYPlot plot, Color[] colors) {
        plot.setBackgroundPaint(Color.WHITE);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int i = 0; i < colors.length; i++) {
            renderer.setSeriesPaint(i, colors[i % colors.length]);
            renderer.setSeriesStroke(i, new BasicStroke(2f));
        }
        plot.setRenderer(renderer);
        ((NumberAxis) plot.getRangeAxis()).setLowerBound(0);
    }

    public static JFreeChart createPauseTimelineChart(List<GcPauseEvent> events) {
        XYSeries series = new XYSeries("Pause Duration (ms)");
        for (GcPauseEvent event : events) {
            series.add(event.uptimeSeconds(), event.pauseMs());
        }

        JFreeChart chart = org.jfree.chart.ChartFactory.createXYLineChart(
                "GC Pause Durations",
                "Uptime (seconds)",
                "Pause (ms)",
                new XYSeriesCollection(series),
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        renderer.setSeriesPaint(0, new Color(120, 52, 198));
        plot.setRenderer(renderer);
        return chart;
    }

    public static String formatMb(long bytes) {
        return new DecimalFormat("#,##0.0").format(bytes / (1024.0 * 1024.0)) + " MB";
    }

    public static JFreeChart createJvmMemorySizeChart(JvmMemoryStats stats) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        addMemorySegment(dataset, "allocated", "Young", stats.young().allocatedBytes(), stats.young().allocatedKnown());
        addMemorySegment(dataset, "allocated", "Old", stats.old().allocatedBytes(), stats.old().allocatedKnown());
        addMemorySegment(dataset, "allocated", "Humongous", stats.humongous().allocatedBytes(), stats.humongous().allocatedKnown());
        addMemorySegment(dataset, "allocated", "Meta", stats.metaspace().allocatedBytes(), stats.metaspace().allocatedKnown());
        addMemorySegment(dataset, "peak usage", "Young", stats.young().peakBytes(), stats.young().peakBytes() > 0);
        addMemorySegment(dataset, "peak usage", "Old", stats.old().peakBytes(), stats.old().peakBytes() > 0);
        addMemorySegment(dataset, "peak usage", "Humongous", stats.humongous().peakBytes(), stats.humongous().peakBytes() > 0);
        addMemorySegment(dataset, "peak usage", "Meta", stats.metaspace().peakBytes(), stats.metaspace().peakBytes() > 0);

        JFreeChart chart = ChartFactory.createStackedBarChart(
                "JVM Memory Size — Allocated vs Peak (MB)",
                "",
                "MB",
                dataset,
                PlotOrientation.HORIZONTAL,
                true,
                true,
                false
        );
        CategoryPlot plot = chart.getCategoryPlot();
        plot.setBackgroundPaint(Color.WHITE);
        StackedBarRenderer renderer = (StackedBarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, YOUNG_COLOR);
        renderer.setSeriesPaint(1, OLD_COLOR);
        renderer.setSeriesPaint(2, HUM_COLOR);
        renderer.setSeriesPaint(3, META_COLOR);
        renderer.setMaximumBarWidth(0.35);
        if (plot.getRangeAxis() instanceof NumberAxis valueAxis) {
            valueAxis.setLowerBound(0);
        }
        ChartAxisConstraints.register(chart, List.of());
        return chart;
    }

    private static final Color YOUNG_COLOR = new Color(0, 150, 136);
    private static final Color OLD_COLOR = new Color(25, 118, 210);
    private static final Color HUM_COLOR = new Color(123, 31, 162);
    private static final Color META_COLOR = new Color(139, 195, 74);

    private static void addMemorySegment(DefaultCategoryDataset dataset, String row, String segment,
                                         long bytes, boolean include) {
        if (include && bytes > 0) {
            dataset.addValue(bytes / (1024.0 * 1024.0), segment, row);
        }
    }

    public static JFreeChart createHeapAfterGcChart(List<GcPauseEvent> events) {
        return createGcTimeSeries("Heap after GC", "(mb)", events,
                e -> e.heapAfterBytes() / (1024.0 * 1024.0), new Color(156, 39, 176));
    }

    public static JFreeChart createHeapBeforeGcChart(List<GcPauseEvent> events) {
        return createGcTimeSeries("Heap before GC", "(mb)", events,
                e -> e.heapBeforeBytes() / (1024.0 * 1024.0), new Color(211, 47, 47));
    }

    public static JFreeChart createPauseGcDurationChart(List<GcPauseEvent> events) {
        return createGcTimeSeries("Pause GC Duration", "(ms)", events, GcPauseEvent::pauseMs,
                new Color(120, 52, 198));
    }

    public static JFreeChart createReclaimedBytesChart(List<GcPauseEvent> events) {
        return createGcTimeSeries("Reclaimed Bytes", "(mb)", events,
                e -> e.heapReclaimedBytes() / (1024.0 * 1024.0), new Color(0, 121, 107));
    }

    public static JFreeChart createYoungGenChart(List<GcPauseEvent> events, long regionSizeBytes) {
        double regionMb = regionSizeBytes / (1024.0 * 1024.0);
        EventTimeline timeline = EventTimeline.from(events);
        TimeSeries allocated = new TimeSeries("allocated space");
        TimeSeries before = new TimeSeries("before GC");
        TimeSeries after = new TimeSeries("after GC");

        for (GcPauseEvent e : events) {
            if (e.edenRegionsCap() == null && e.edenRegionsBefore() == null) {
                continue;
            }
            Date t = timeline.dateFor(e);
            if (e.edenRegionsCap() != null) {
                int cap = e.edenRegionsCap() + nz(e.survivorRegionsCap());
                allocated.addOrUpdate(new Millisecond(t), cap * regionMb);
            }
            if (e.edenRegionsBefore() != null) {
                int used = e.edenRegionsBefore() + nz(e.survivorRegionsBefore());
                before.addOrUpdate(new Millisecond(t), used * regionMb);
            }
            if (e.edenRegionsAfter() != null) {
                after.addOrUpdate(new Millisecond(t), e.edenRegionsAfter() * regionMb);
            }
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(allocated);
        dataset.addSeries(before);
        dataset.addSeries(after);
        return buildDateChart("Young Gen", "(mb)", dataset,
                new Color[]{YOUNG_COLOR, new Color(211, 47, 47), new Color(156, 39, 176)}, events);
    }

    public static JFreeChart createMetaSpaceTabChart(List<GcPauseEvent> events) {
        EventTimeline timeline = EventTimeline.from(events);
        TimeSeries capacity = new TimeSeries("allocated space");
        TimeSeries used = new TimeSeries("after GC");

        for (GcPauseEvent e : events) {
            if (e.metaspaceUsedAfterKb() == null) {
                continue;
            }
            Date t = timeline.dateFor(e);
            if (e.metaspaceCapacityKb() != null) {
                capacity.addOrUpdate(new Millisecond(t), e.metaspaceCapacityKb() / 1024.0);
            }
            used.addOrUpdate(new Millisecond(t), e.metaspaceUsedAfterKb() / 1024.0);
        }

        TimeSeriesCollection dataset = new TimeSeriesCollection();
        dataset.addSeries(capacity);
        dataset.addSeries(used);
        return buildDateChart("Meta Space", "(mb)", dataset, new Color[]{META_COLOR, new Color(0, 150, 136)}, events);
    }

    private static JFreeChart createGcTimeSeries(String title, String yLabel, List<GcPauseEvent> events,
                                                 ToDoubleFunction<GcPauseEvent> valueFn, Color color) {
        EventTimeline timeline = EventTimeline.from(events);
        TimeSeries series = new TimeSeries(title);
        for (GcPauseEvent e : events) {
            series.addOrUpdate(new Millisecond(timeline.dateFor(e)), valueFn.applyAsDouble(e));
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection(series);
        return buildDateChart(title, yLabel, dataset, new Color[]{color}, events);
    }

    private static JFreeChart buildDateChart(String title, String yLabel, TimeSeriesCollection dataset,
                                             Color[] colors, List<GcPauseEvent> events) {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title, "Time (GC log timestamp)", yLabel, dataset, true, true, false);
        XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.WHITE);
        DateAxis domain = (DateAxis) plot.getDomainAxis();
        domain.setDateFormatOverride(new SimpleDateFormat("MMM dd, HH:mm:ss"));
        long minTime = ChartAxisConstraints.minLogTimeMillis(events);
        if (minTime > 0) {
            domain.setMinimumDate(new Date(minTime));
        }
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, true);
        for (int i = 0; i < colors.length; i++) {
            renderer.setSeriesPaint(i, colors[i]);
            renderer.setSeriesStroke(i, new BasicStroke(2f));
        }
        plot.setRenderer(renderer);
        NumberAxis range = (NumberAxis) plot.getRangeAxis();
        range.setLowerBound(0);
        range.setAutoRangeIncludesZero(true);
        ChartAxisConstraints.register(chart, events);
        return chart;
    }

    private record EventTimeline(long anchorEpochMs, double minUptime) {
        static EventTimeline from(List<GcPauseEvent> events) {
            long anchor = ChartAxisConstraints.minLogTimeMillis(events);
            if (anchor < 0) {
                anchor = System.currentTimeMillis();
            }
            double minUp = ChartAxisConstraints.minUptime(events);
            return new EventTimeline(anchor, minUp);
        }

        Date dateFor(GcPauseEvent e) {
            if (ChartAxisConstraints.validTimestamp(e.timestamp())) {
                return Date.from(e.timestamp());
            }
            long ms = anchorEpochMs + (long) ((e.uptimeSeconds() - minUptime) * 1000.0);
            return new Date(ms);
        }
    }

    private static int nz(Integer v) {
        return v != null ? v : 0;
    }
}
