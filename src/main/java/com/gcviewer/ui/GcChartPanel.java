package com.gcviewer.ui;

import com.gcviewer.model.GcPauseEvent;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import java.util.List;

public class GcChartPanel extends ChartPanel {
    private List<GcPauseEvent> events = List.of();

    public GcChartPanel() {
        super(null);
        setMouseWheelEnabled(true);
        setFillZoomRectangle(true);
        setPreferredSize(new java.awt.Dimension(800, 380));
    }

    public void setChart(JFreeChart chart, List<GcPauseEvent> events) {
        this.events = events != null ? events : List.of();
        setChart(chart);
        if (chart != null) {
            ChartAxisConstraints.register(chart, this.events);
        }
    }
}
