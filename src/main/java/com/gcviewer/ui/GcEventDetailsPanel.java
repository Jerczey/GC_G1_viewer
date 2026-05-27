package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcPauseEvent;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

public class GcEventDetailsPanel extends JPanel {
    private final GcEventTablePanel tablePanel = new GcEventTablePanel();
    private final GcEventDetailPanel detailPanel = new GcEventDetailPanel();

    public GcEventDetailsPanel() {
        super(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, detailPanel);
        split.setResizeWeight(0.55);
        split.setDividerLocation(280);
        add(split, BorderLayout.CENTER);
    }

    public void setSelectionListener(Consumer<GcPauseEvent> listener) {
        tablePanel.setSelectionListener(listener);
    }

    public void update(GcLogModel model) {
        tablePanel.update(model);
        if (!model.getPauseEvents().isEmpty()) {
            detailPanel.showEvent(model.getPauseEvents().get(0));
        } else {
            detailPanel.clear();
        }
    }

    public void showEventDetail(GcPauseEvent event) {
        detailPanel.showEvent(event);
    }
}
