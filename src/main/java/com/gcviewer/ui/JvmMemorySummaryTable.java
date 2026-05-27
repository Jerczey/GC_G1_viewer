package com.gcviewer.ui;

import com.gcviewer.model.GenerationMemoryStats;
import com.gcviewer.model.JvmMemoryStats;
import com.gcviewer.util.LogFileFormat;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class JvmMemorySummaryTable extends JPanel {
    private final MemoryTableModel tableModel = new MemoryTableModel();

    public JvmMemorySummaryTable() {
        super(new BorderLayout());
        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(new TitledBorder("JVM Memory Size — Summary"));
        scroll.setPreferredSize(new Dimension(400, 140));
        add(scroll, BorderLayout.CENTER);
    }

    public void setStats(JvmMemoryStats stats) {
        tableModel.setStats(stats);
    }

    private static final class MemoryTableModel extends AbstractTableModel {
        private final String[] columns = {"Generation", "Allocated", "Peak"};
        private GenerationMemoryStats[] rows = new GenerationMemoryStats[0];

        void setStats(JvmMemoryStats stats) {
            rows = new GenerationMemoryStats[]{
                    stats.young(), stats.old(), stats.humongous(), stats.metaspace(), stats.total()
            };
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.length;
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
            GenerationMemoryStats row = rows[rowIndex];
            return switch (columnIndex) {
                case 0 -> row.generation();
                case 1 -> formatCell(row.allocatedBytes(), row.allocatedKnown());
                case 2 -> formatCell(row.peakBytes(), row.peakBytes() > 0);
                default -> "";
            };
        }

        private static String formatCell(long bytes, boolean known) {
            if (!known || bytes <= 0) {
                return "n/a";
            }
            return LogFileFormat.formatSize(bytes);
        }
    }
}
