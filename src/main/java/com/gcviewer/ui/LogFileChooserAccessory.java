package com.gcviewer.ui;

import com.gcviewer.util.LogFileFormat;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;

public class LogFileChooserAccessory extends JPanel {
    private final JLabel fileNameLabel = new JLabel("—");
    private final JLabel sizeLabel = new JLabel("—");
    private final JLabel modifiedLabel = new JLabel("—");
    private final JLabel pathLabel = new JLabel(" ");

    public LogFileChooserAccessory(JFileChooser chooser) {
        super(new BorderLayout(8, 8));
        setBorder(new TitledBorder("File details"));
        setPreferredSize(new Dimension(280, 120));

        JPanel grid = new JPanel(new GridLayout(0, 1, 4, 4));
        grid.setBorder(new EmptyBorder(4, 8, 8, 8));
        grid.add(row("Name:", fileNameLabel));
        grid.add(row("Size:", sizeLabel));
        grid.add(row("Modified:", modifiedLabel));

        pathLabel.setFont(pathLabel.getFont().deriveFont(10f));
        pathLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        pathLabel.setBorder(new EmptyBorder(0, 8, 8, 8));

        add(grid, BorderLayout.CENTER);
        add(pathLabel, BorderLayout.SOUTH);

        chooser.addPropertyChangeListener(evt -> {
            if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY.equals(evt.getPropertyName())) {
                Object value = evt.getNewValue();
                if (value instanceof File file) {
                    showFile(file.toPath());
                } else {
                    clear();
                }
            }
        });

        File selected = chooser.getSelectedFile();
        if (selected != null) {
            showFile(selected.toPath());
        }
    }

    private static JPanel row(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        JLabel t = new JLabel(title);
        t.setFont(t.getFont().deriveFont(Font.BOLD, 11f));
        value.setFont(value.getFont().deriveFont(11f));
        p.add(t, BorderLayout.WEST);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private void showFile(Path path) {
        fileNameLabel.setText(path.getFileName().toString());
        long size = LogFileFormat.sizeOf(path);
        sizeLabel.setText(size >= 0 ? LogFileFormat.formatSize(size) : "Unknown");
        modifiedLabel.setText(LogFileFormat.formatModified(LogFileFormat.lastModified(path)));
        pathLabel.setText("<html>" + escape(path.toAbsolutePath().toString()) + "</html>");
    }

    private void clear() {
        fileNameLabel.setText("—");
        sizeLabel.setText("—");
        modifiedLabel.setText("—");
        pathLabel.setText("Select a GC log file");
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;");
    }
}
