package com.gcviewer.ui;

import com.gcviewer.metrics.JvmMemoryStatsCalculator;
import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GenerationMemoryStats;
import com.gcviewer.model.JvmMemoryStats;
import com.gcviewer.util.LogFileFormat;
import org.jfree.chart.ChartPanel;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;

public class JvmMemorySizePanel extends JPanel {
    private final JvmMemoryStatsCalculator calculator = new JvmMemoryStatsCalculator();
    private final MemoryTableModel tableModel = new MemoryTableModel();
    private final ChartPanel chartPanel = new ChartPanel(null);

    public JvmMemorySizePanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel title = new JLabel("JVM Memory Size");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));

        JTable table = new JTable(tableModel);
        table.setRowHeight(24);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(new TitledBorder("Summary"));
        tableScroll.setPreferredSize(new Dimension(400, 130));

        chartPanel.setMouseWheelEnabled(true);
        chartPanel.setBorder(new TitledBorder("Allocated vs Peak (MB)"));

        JPanel top = new JPanel(new BorderLayout());
        top.add(title, BorderLayout.NORTH);
        top.add(tableScroll, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(chartPanel, BorderLayout.CENTER);
    }

    public void update(GcLogModel model) {
        JvmMemoryStats stats = calculator.calculate(model);
        tableModel.setStats(stats);
        chartPanel.setChart(ChartFactoryUtil.createJvmMemorySizeChart(stats));
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
