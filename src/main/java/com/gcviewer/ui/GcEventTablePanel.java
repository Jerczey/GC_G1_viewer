package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcPauseEvent;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GcEventTablePanel extends JPanel {
    private static final DecimalFormat DF = new DecimalFormat("#,##0.##");

    private final GcEventTableModel tableModel = new GcEventTableModel();
    private final JTable table = new JTable(tableModel);
    private Consumer<GcPauseEvent> selectionListener = e -> {};

    public GcEventTablePanel() {
        super(new BorderLayout());
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 0; i < table.getColumnCount(); i++) {
            if (i != 2 && i != 3) {
                table.getColumnModel().getColumn(i).setCellRenderer(right);
            }
        }
        setColumnWidths();

        table.getSelectionModel().addListSelectionListener(this::onSelect);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    public void setSelectionListener(Consumer<GcPauseEvent> listener) {
        this.selectionListener = listener != null ? listener : e -> {};
    }

    public void update(GcLogModel model) {
        tableModel.setEvents(model.getPauseEvents());
        if (!model.getPauseEvents().isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
    }

    private void onSelect(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) {
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0) {
            selectionListener.accept(null);
            return;
        }
        selectionListener.accept(tableModel.getAt(row));
    }

    private void setColumnWidths() {
        int[] widths = {40, 70, 120, 140, 75, 75, 75, 65, 45, 45, 45, 55, 55, 70};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static final class GcEventTableModel extends AbstractTableModel {
        private final String[] columns = {
                "GC#", "Uptime", "Type", "Reason", "Pause ms", "Before", "After",
                "Reclaimed", "Eden", "Old", "Hum", "Meta K", "CPU Real", "Workers"
        };
        private List<GcPauseEvent> events = List.of();

        void setEvents(List<GcPauseEvent> events) {
            this.events = new ArrayList<>(events);
            fireTableDataChanged();
        }

        GcPauseEvent getAt(int row) {
            return events.get(row);
        }

        @Override
        public int getRowCount() {
            return events.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            GcPauseEvent e = events.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> e.gcId();
                case 1 -> DF.format(e.uptimeSeconds());
                case 2 -> e.pauseType();
                case 3 -> e.reason();
                case 4 -> DF.format(e.pauseMs());
                case 5 -> formatMbShort(e.heapBeforeBytes());
                case 6 -> formatMbShort(e.heapAfterBytes());
                case 7 -> formatMbShort(e.heapReclaimedBytes());
                case 8 -> regionStr(e.edenRegionsBefore(), e.edenRegionsAfter());
                case 9 -> regionStr(e.oldRegionsBefore(), e.oldRegionsAfter());
                case 10 -> regionStr(e.humongousRegionsBefore(), e.humongousRegionsAfter());
                case 11 -> e.metaspaceUsedAfterKb() != null ? e.metaspaceUsedAfterKb() : "";
                case 12 -> e.cpuRealSec() != null ? DF.format(e.cpuRealSec()) + "s" : "";
                case 13 -> e.workers() != null ? e.workers() : "";
                default -> "";
            };
        }

        private static String formatMbShort(long bytes) {
            return DF.format(bytes / (1024.0 * 1024.0));
        }

        private static String regionStr(Integer before, Integer after) {
            if (before == null && after == null) {
                return "";
            }
            return (before != null ? before : "?") + ">" + (after != null ? after : "?");
        }
    }
}
