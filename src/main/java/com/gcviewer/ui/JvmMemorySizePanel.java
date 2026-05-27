package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class JvmMemorySizePanel extends JPanel {
    private final GcChartPanel heapChartPanel = new GcChartPanel();

    public JvmMemorySizePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        heapChartPanel.setBorder(new TitledBorder("Heap Usage Over Time"));
        add(heapChartPanel, BorderLayout.CENTER);
    }

    public void update(GcLogModel model) {
        heapChartPanel.setChart(ChartFactoryUtil.createHeapChart(model), model.getPauseEvents());
    }
}
