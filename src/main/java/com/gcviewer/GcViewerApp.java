package com.gcviewer;

import com.gcviewer.ui.MainFrame;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GcViewerApp {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        boolean watch = false;
        List<String> paths = new ArrayList<>();
        for (String arg : args) {
            if ("--watch".equals(arg) || "-w".equals(arg)) {
                watch = true;
            } else if (!arg.startsWith("-")) {
                paths.add(arg);
            }
        }

        boolean watchMode = watch;
        Path fileArg = paths.isEmpty() ? null : Path.of(paths.get(0));

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            MainFrame frame = new MainFrame();
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            if (fileArg != null) {
                frame.loadFileFromArgs(fileArg, watchMode);
            }
        });
    }
}
