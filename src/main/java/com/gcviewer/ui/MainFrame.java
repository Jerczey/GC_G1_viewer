package com.gcviewer.ui;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcPauseEvent;
import com.gcviewer.parser.GcLogTailer;
import com.gcviewer.parser.UnifiedGcLogParser;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.Action;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.prefs.Preferences;

public class MainFrame extends JFrame {
    private final UnifiedGcLogParser parser = new UnifiedGcLogParser();
    private final MetricsPanel metricsPanel = new MetricsPanel();
    private final GcEventTablePanel eventTablePanel = new GcEventTablePanel();
    private final GcEventDetailPanel detailPanel = new GcEventDetailPanel();

    private ChartPanel heapChartPanel;
    private ChartPanel pauseBreakdownPanel;
    private ChartPanel pauseTimelinePanel;
    private ChartPanel regionsChartPanel;
    private ChartPanel metaspaceChartPanel;
    private ChartPanel pauseByTypeChartPanel;
    private ChartPanel phaseChartPanel;

    private GcLogModel currentModel;
    private GcPauseEvent selectedEvent;

    private GcLogTailer tailer;
    private Timer tailTimer;
    private Path currentPath;
    private final Preferences prefs = Preferences.userNodeForPackage(MainFrame.class);

    public MainFrame() {
        super("GC G1 Viewer — JBoss / Red Hat SSO");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 750));
        setLocationByPlatform(true);

        JMenuBar menuBar = buildMenuBar();
        setJMenuBar(menuBar);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(new EmptyBorder(8, 8, 0, 8));
        top.add(buildToolbar(), BorderLayout.NORTH);
        top.add(metricsPanel, BorderLayout.CENTER);

        JTabbedPane charts = new JTabbedPane();
        heapChartPanel = createChartPanel(null);
        regionsChartPanel = createChartPanel(null);
        metaspaceChartPanel = createChartPanel(null);
        pauseBreakdownPanel = createChartPanel(null);
        pauseByTypeChartPanel = createChartPanel(null);
        pauseTimelinePanel = createChartPanel(null);
        phaseChartPanel = createChartPanel(null);
        charts.addTab("Heap Usage", heapChartPanel);
        charts.addTab("G1 Regions", regionsChartPanel);
        charts.addTab("Metaspace", metaspaceChartPanel);
        charts.addTab("Pause by Reason", pauseBreakdownPanel);
        charts.addTab("Pause by Type", pauseByTypeChartPanel);
        charts.addTab("Pause Timeline", pauseTimelinePanel);
        charts.addTab("GC Phases", phaseChartPanel);

        eventTablePanel.setSelectionListener(event -> {
            selectedEvent = event;
            detailPanel.showEvent(event);
            if (currentModel != null) {
                phaseChartPanel.setChart(ChartFactoryUtil.createPhaseChart(event));
            }
        });

        JSplitPane tableDetail = new JSplitPane(JSplitPane.VERTICAL_SPLIT, eventTablePanel, detailPanel);
        tableDetail.setResizeWeight(0.55);
        tableDetail.setDividerLocation(220);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, charts, tableDetail);
        split.setResizeWeight(0.58);
        split.setDividerLocation(400);

        getContentPane().setLayout(new BorderLayout(8, 8));
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);

        String lastFile = prefs.get("lastGcLog", "");
        if (!lastFile.isBlank()) {
            loadFileQuietly(Path.of(lastFile));
        } else {
            autoLoadSampleLogs();
        }
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem open = new JMenuItem("Open GC Log...");
        open.setAccelerator(KeyStroke.getKeyStroke("control O"));
        open.addActionListener(e -> openFile());
        JMenuItem watch = new JMenuItem("Watch GC Log (Real-time)...");
        watch.setAccelerator(KeyStroke.getKeyStroke("control W"));
        watch.addActionListener(e -> watchFile());
        JMenuItem stop = new JMenuItem("Stop Watching");
        stop.addActionListener(e -> stopWatching());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> {
            stopWatching();
            dispose();
        });
        file.add(open);
        file.add(watch);
        file.add(stop);
        file.addSeparator();
        file.add(exit);
        bar.add(file);

        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                """
                        GC G1 Viewer 1.0
                        Parses JDK unified G1 GC logs from JBoss / Red Hat SSO.

                        Enable logging on the JVM:
                        -Xlog:gc*,safepoint:file=/path/gc-%t.log:time,uptime,level,tags

                        Features: heap charts, pause breakdown, throughput metrics, live tail.
                        """,
                "About GC G1 Viewer",
                JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(help);
        return bar;
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton openBtn = new JButton("Open Log");
        openBtn.addActionListener(e -> openFile());
        JButton watchBtn = new JButton("Watch Live");
        watchBtn.addActionListener(e -> watchFile());
        JButton stopBtn = new JButton("Stop Watch");
        stopBtn.addActionListener(e -> stopWatching());
        JButton refreshBtn = new JButton("Refresh");
        refreshBtn.addActionListener(e -> refreshCurrentFile());
        toolbar.add(openBtn);
        toolbar.add(watchBtn);
        toolbar.add(stopBtn);
        toolbar.add(refreshBtn);
        return toolbar;
    }

    private ChartPanel createChartPanel(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setMouseWheelEnabled(true);
        panel.setPreferredSize(new Dimension(800, 320));
        panel.setFillZoomRectangle(true);
        return panel;
    }

    private void autoLoadSampleLogs() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        try (var stream = java.nio.file.Files.list(cwd)) {
            stream.filter(p -> p.getFileName().toString().startsWith("gc-") && p.toString().endsWith(".log"))
                    .sorted()
                    .findFirst()
                    .ifPresent(this::loadFile);
        } catch (IOException ignored) {
        }
    }

    private JFileChooser createLogFileChooser(String title) {
        JFileChooser chooser = new JFileChooser(prefs.get("lastDir", "."));
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(false);
        chooser.setFileFilter(new FileNameExtensionFilter("GC log files (*.log)", "log", "txt"));
        chooser.setAccessory(new LogFileChooserAccessory(chooser));
        chooser.putClientProperty("JFileChooser.useDetailsView", Boolean.TRUE);
        enableFileChooserDetailsView(chooser);
        return chooser;
    }

    private static void enableFileChooserDetailsView(JFileChooser chooser) {
        Action details = chooser.getActionMap().get("viewDetails");
        if (details != null && !Boolean.TRUE.equals(details.getValue(Action.SELECTED_KEY))) {
            details.actionPerformed(new java.awt.event.ActionEvent(chooser, ActionEvent.ACTION_PERFORMED, "viewDetails"));
        }
    }

    private void openFile() {
        JFileChooser chooser = createLogFileChooser("Open GC Log");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            stopWatching();
            loadFile(chooser.getSelectedFile().toPath());
            prefs.put("lastDir", chooser.getSelectedFile().getParent());
        }
    }

    private void watchFile() {
        JFileChooser chooser = createLogFileChooser("Watch GC Log (real-time)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            Path path = chooser.getSelectedFile().toPath();
            prefs.put("lastDir", path.getParent().toString());
            startWatching(path);
        }
    }

    private void startWatching(Path path) {
        stopWatching();
        currentPath = path;
        try {
            tailer = new GcLogTailer(path);
            tailer.start(this::applyModel);
            tailTimer = new Timer(1000, e -> {
                try {
                    tailer.poll(model -> SwingUtilities.invokeLater(() -> applyModel(model)));
                } catch (IOException ex) {
                    statusError("Tail error: " + ex.getMessage());
                }
            });
            tailTimer.start();
            setTitle("GC G1 Viewer — WATCHING " + path.getFileName());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Watch failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopWatching() {
        if (tailTimer != null) {
            tailTimer.stop();
            tailTimer = null;
        }
        if (tailer != null) {
            try {
                tailer.close();
            } catch (IOException ignored) {
            }
            tailer = null;
        }
        if (currentPath != null) {
            setTitle("GC G1 Viewer — " + currentPath.getFileName());
        }
    }

    private void refreshCurrentFile() {
        if (currentPath != null) {
            loadFile(currentPath);
        }
    }

    public void loadFileFromArgs(Path path, boolean watch) {
        if (watch) {
            startWatching(path);
        } else {
            stopWatching();
            loadFile(path);
        }
    }

    private void loadFileQuietly(Path path) {
        try {
            loadFile(path);
        } catch (Exception ignored) {
        }
    }

    private void loadFile(Path path) {
        currentPath = path;
        prefs.put("lastGcLog", path.toString());
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<GcLogModel, Void> worker = new SwingWorker<>() {
            @Override
            protected GcLogModel doInBackground() throws Exception {
                return parser.parseFile(path);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    applyModel(get());
                    setTitle("GC G1 Viewer — " + path.getFileName());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(MainFrame.this,
                            "Failed to parse GC log:\n" + ex.getMessage(),
                            "Parse Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void applyModel(GcLogModel model) {
        currentModel = model;
        selectedEvent = null;
        metricsPanel.update(model);
        eventTablePanel.update(model);
        var events = model.getPauseEvents();
        heapChartPanel.setChart(ChartFactoryUtil.createHeapChart(model));
        regionsChartPanel.setChart(ChartFactoryUtil.createG1RegionsChart(model, events));
        metaspaceChartPanel.setChart(ChartFactoryUtil.createMetaspaceChart(events));
        pauseBreakdownPanel.setChart(ChartFactoryUtil.createPauseBreakdownChart(model.getMetrics()));
        pauseByTypeChartPanel.setChart(ChartFactoryUtil.createPauseByTypeChart(model.getMetrics()));
        pauseTimelinePanel.setChart(ChartFactoryUtil.createPauseTimelineChart(events));
        if (!events.isEmpty() && selectedEvent == null) {
            selectedEvent = events.get(0);
        }
        detailPanel.showEvent(selectedEvent);
        phaseChartPanel.setChart(ChartFactoryUtil.createPhaseChart(selectedEvent));
    }

    private void statusError(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
