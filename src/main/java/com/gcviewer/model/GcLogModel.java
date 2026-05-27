package com.gcviewer.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GcLogModel {
    private final Path sourcePath;
    private HeapInfo heapInfo;
    private final List<GcPauseEvent> pauseEvents = new ArrayList<>();
    private final List<HeapSample> heapSamples = new ArrayList<>();
    private GcMetrics metrics = GcMetrics.empty();
    private long linesParsed;
    private long fileSizeBytes = -1;
    private Instant fileLastModified;

    public GcLogModel(Path sourcePath) {
        this.sourcePath = sourcePath;
    }

    public Path getSourcePath() {
        return sourcePath;
    }

    public HeapInfo getHeapInfo() {
        return heapInfo;
    }

    public void setHeapInfo(HeapInfo heapInfo) {
        this.heapInfo = heapInfo;
    }

    public List<GcPauseEvent> getPauseEvents() {
        return Collections.unmodifiableList(pauseEvents);
    }

    public List<HeapSample> getHeapSamples() {
        return Collections.unmodifiableList(heapSamples);
    }

    public GcMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(GcMetrics metrics) {
        this.metrics = metrics;
    }

    public long getLinesParsed() {
        return linesParsed;
    }

    public void setLinesParsed(long linesParsed) {
        this.linesParsed = linesParsed;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public Instant getFileLastModified() {
        return fileLastModified;
    }

    public void setFileLastModified(Instant fileLastModified) {
        this.fileLastModified = fileLastModified;
    }

    public void addPauseEvent(GcPauseEvent event) {
        pauseEvents.add(event);
        heapSamples.add(new HeapSample(event.uptimeSeconds(), event.heapBeforeBytes(), event.heapCapacityBytes(), false));
        heapSamples.add(new HeapSample(event.uptimeSeconds(), event.heapAfterBytes(), event.heapCapacityBytes(), true));
    }

    public void clearEvents() {
        pauseEvents.clear();
        heapSamples.clear();
        metrics = GcMetrics.empty();
    }

    public void replaceFrom(GcLogModel other) {
        heapInfo = other.heapInfo;
        pauseEvents.clear();
        pauseEvents.addAll(other.pauseEvents);
        heapSamples.clear();
        heapSamples.addAll(other.heapSamples);
        metrics = other.metrics;
        linesParsed = other.linesParsed;
    }
}
