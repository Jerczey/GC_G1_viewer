package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.parser.UnifiedGcLogParser;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HeapChartDualAxisTest {

    private final UnifiedGcLogParser parser = new UnifiedGcLogParser();
    private final Path sample = Path.of("gc-2026-05-27_12-20-09.log");

    @Test
    void heapChartHasUptimeAndDateDomainAxes() throws Exception {
        GcLogModel model = parser.parseFile(sample);
        assertFalse(model.getPauseEvents().isEmpty());

        JFreeChart chart = ChartFactoryUtil.createHeapChart(model);
        XYPlot plot = chart.getXYPlot();

        assertInstanceOf(NumberAxis.class, plot.getDomainAxis(0));
        assertNotNull(plot.getDomainAxis(1), "top date axis should exist");
        assertInstanceOf(DateAxis.class, plot.getDomainAxis(1));

        NumberAxis uptime = (NumberAxis) plot.getDomainAxis(0);
        DateAxis date = (DateAxis) plot.getDomainAxis(1);

        assertTrue(uptime.getUpperBound() > uptime.getLowerBound(),
                "uptime axis needs valid range, got " + uptime.getLowerBound() + ".." + uptime.getUpperBound());
        assertTrue(date.getMaximumDate().after(date.getMinimumDate()),
                "date axis needs valid range");

        assertEquals(0.0, uptime.getLowerBound(), 0.001);
        assertTrue(date.getLabel().contains("Time"));
        assertEquals("Uptime (seconds)", uptime.getLabel());
    }
}
