package com.gcviewer.ui;

import com.gcviewer.metrics.JvmMemoryStatsCalculator;
import com.gcviewer.model.GcLogModel;

import javax.swing.*;
import java.awt.*;

public class AllocatedVsPeakPanel extends JPanel {
    private final JvmMemoryStatsCalculator calculator = new JvmMemoryStatsCalculator();
    private final JvmMemorySummaryTable summaryTable = new JvmMemorySummaryTable();
    private final GcChartPanel chartPanel = new GcChartPanel();

    public AllocatedVsPeakPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        add(summaryTable, BorderLayout.NORTH);
        add(chartPanel, BorderLayout.CENTER);
    }

    public void update(GcLogModel model) {
        var stats = calculator.calculate(model);
        summaryTable.setStats(stats);
        chartPanel.setChart(ChartFactoryUtil.createJvmMemorySizeChart(stats), model.getPauseEvents());
    }
}
