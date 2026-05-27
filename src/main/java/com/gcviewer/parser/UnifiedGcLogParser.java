package com.gcviewer.parser;

import com.gcviewer.metrics.GcMetricsCalculator;
import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcPauseEvent;
import com.gcviewer.model.HeapInfo;
import com.gcviewer.util.MemorySizeParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UnifiedGcLogParser {
    private static final Pattern LINE =
            Pattern.compile("^\\[([^\\]]+)]\\[([\\d.]+)s]\\[\\w+]\\[([^\\]]+)]\\s+(.*)$");

    private static final Pattern GC_PAUSE = Pattern.compile(
            "GC\\((\\d+)\\)\\s+(.+?)\\s+([\\d.]+[KMG])->([\\d.]+[KMG])\\(([\\d.]+[KMG])\\)\\s+([\\d.]+)ms");

    private static final Pattern HEAP_SIZE_MB =
            Pattern.compile("size:\\s*(\\d+)\\s*MB", Pattern.CASE_INSENSITIVE);

    private static final Pattern HEAP_ADDRESS =
            Pattern.compile("Heap address:\\s*(\\S+),\\s*size:\\s*(\\d+)\\s*MB.*Compressed Oops mode:\\s*(\\S+)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern REGION_SIZE =
            Pattern.compile("Heap region size:\\s*(\\d+)M", Pattern.CASE_INSENSITIVE);

    private static final Pattern USING_G1 =
            Pattern.compile("Using G1", Pattern.CASE_INSENSITIVE);

    private static final Pattern EDEN_REGIONS =
            Pattern.compile("GC\\((\\d+)\\)\\s+Eden regions:\\s*(\\d+)->(\\d+)\\((\\d+)\\)");

    private static final Pattern SURVIVOR_REGIONS =
            Pattern.compile("GC\\((\\d+)\\)\\s+Survivor regions:\\s*(\\d+)->(\\d+)\\((\\d+)\\)");

    private static final Pattern OLD_REGIONS =
            Pattern.compile("GC\\((\\d+)\\)\\s+Old regions:\\s*(\\d+)->(\\d+)");

    private static final Pattern HUMONGOUS_REGIONS =
            Pattern.compile("GC\\((\\d+)\\)\\s+Humongous regions:\\s*(\\d+)->(\\d+)");

    private static final Pattern METASPACE = Pattern.compile(
            "GC\\((\\d+)\\)\\s+Metaspace:\\s*(\\d+)K\\((\\d+)K\\)->(\\d+)K\\((\\d+)K\\).*Class:\\s*(\\d+)K");

    private static final Pattern GC_PHASE =
            Pattern.compile("GC\\((\\d+)\\)\\s+(.+?):\\s*([\\d.]+)ms");

    private static final Pattern GC_CPU =
            Pattern.compile("GC\\((\\d+)\\)\\s+User=([\\d.]+)s\\s+Sys=([\\d.]+)s\\s+Real=([\\d.]+)s");

    private static final Pattern GC_WORKERS =
            Pattern.compile("GC\\((\\d+)\\)\\s+Using\\s+(\\d+)\\s+workers?\\s+of\\s+(\\d+)\\s+for\\s+(\\w+)",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern CONCURRENT_CYCLE =
            Pattern.compile("GC\\((\\d+)\\)\\s+Concurrent Cycle\\s+([\\d.]+)ms");

    private final GcMetricsCalculator metricsCalculator = new GcMetricsCalculator();

    public GcLogModel parseFile(Path path) throws IOException {
        GcLogModel model = new GcLogModel(path);
        if (Files.exists(path)) {
            model.setFileSizeBytes(Files.size(path));
            model.setFileLastModified(Files.getLastModifiedTime(path).toInstant());
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            parse(reader, model);
        }
        finalizeModel(model);
        return model;
    }

    public void parse(BufferedReader reader, GcLogModel model) throws IOException {
        String line;
        long lineNumber = 0;
        Map<Integer, GcEventBuilder> builders = new HashMap<>();

        while ((line = reader.readLine()) != null) {
            lineNumber++;
            parseLine(line, model, builders);
        }
        model.setLinesParsed(lineNumber);
    }

    private void finalizeModel(GcLogModel model) {
        model.setMetrics(metricsCalculator.calculate(model));
    }

    private void parseLine(String line, GcLogModel model, Map<Integer, GcEventBuilder> builders) {
        Matcher lineMatcher = LINE.matcher(line);
        if (!lineMatcher.matches()) {
            return;
        }

        String timestampText = lineMatcher.group(1);
        double uptimeSeconds = Double.parseDouble(lineMatcher.group(2));
        String tags = lineMatcher.group(3).trim();
        String message = lineMatcher.group(4);
        Instant timestamp = parseTimestamp(timestampText);

        if (tags.contains("gc,heap") && message.startsWith("Heap region size:")) {
            Matcher regionMatcher = REGION_SIZE.matcher(message);
            if (regionMatcher.find()) {
                long regionBytes = Long.parseLong(regionMatcher.group(1)) * 1024L * 1024L;
                mergeHeapInfo(model, regionBytes, -1, null, null, null);
            }
            return;
        }

        if (tags.contains("gc,heap") && message.contains("Heap address:")) {
            Matcher addrMatcher = HEAP_ADDRESS.matcher(message);
            if (addrMatcher.find()) {
                long capacity = Long.parseLong(addrMatcher.group(2)) * 1024L * 1024L;
                mergeHeapInfo(model, -1, capacity, addrMatcher.group(1), addrMatcher.group(3), "G1");
            }
            return;
        }

        if (tags.contains("gc,heap") && message.contains("size:") && message.contains("MB")
                && !message.contains("Heap address")) {
            Matcher heapMatcher = HEAP_SIZE_MB.matcher(message);
            if (heapMatcher.find()) {
                long capacity = Long.parseLong(heapMatcher.group(1)) * 1024L * 1024L;
                mergeHeapInfo(model, -1, capacity, null, null, "G1");
            }
            return;
        }

        if (tags.equals("gc") && USING_G1.matcher(message).find()) {
            mergeHeapInfo(model, -1, -1, null, null, "G1");
            return;
        }

        Matcher cycleMatcher = CONCURRENT_CYCLE.matcher(message);
        if (cycleMatcher.find()) {
            GcEventBuilder builder = builder(builders, Integer.parseInt(cycleMatcher.group(1)), uptimeSeconds, timestamp);
            builder.concurrentCycleMs = Double.parseDouble(cycleMatcher.group(2));
            return;
        }

        Matcher workersMatcher = GC_WORKERS.matcher(message);
        if (workersMatcher.find()) {
            GcEventBuilder builder = builder(builders, Integer.parseInt(workersMatcher.group(1)), uptimeSeconds, timestamp);
            builder.workers = workersMatcher.group(2) + " / " + workersMatcher.group(3) + " (" + workersMatcher.group(4) + ")";
            return;
        }

        if (tags.contains("phases")) {
            Matcher phaseMatcher = GC_PHASE.matcher(message);
            if (phaseMatcher.find()) {
                GcEventBuilder b = builder(builders, Integer.parseInt(phaseMatcher.group(1)), uptimeSeconds, timestamp);
                String phaseName = phaseMatcher.group(2).trim();
                if (!phaseName.isEmpty() && !phaseName.startsWith("Concurrent")) {
                    b.phaseTimingsMs.put(phaseName, Double.parseDouble(phaseMatcher.group(3)));
                }
            }
            return;
        }

        if (tags.contains("heap") && message.contains("regions:")) {
            applyRegionLines(message, builders, uptimeSeconds, timestamp);
            return;
        }

        Matcher metaMatcher = METASPACE.matcher(message);
        if (metaMatcher.find()) {
            GcEventBuilder b = builder(builders, Integer.parseInt(metaMatcher.group(1)), uptimeSeconds, timestamp);
            b.metaspaceUsedBeforeKb = Long.parseLong(metaMatcher.group(2));
            b.metaspaceCapacityKb = Long.parseLong(metaMatcher.group(3));
            b.metaspaceUsedAfterKb = Long.parseLong(metaMatcher.group(4));
            b.metaspaceClassUsedKb = Long.parseLong(metaMatcher.group(6));
            return;
        }

        Matcher cpuMatcher = GC_CPU.matcher(message);
        if (cpuMatcher.find()) {
            GcEventBuilder b = builder(builders, Integer.parseInt(cpuMatcher.group(1)), uptimeSeconds, timestamp);
            b.cpuUserSec = Double.parseDouble(cpuMatcher.group(2));
            b.cpuSysSec = Double.parseDouble(cpuMatcher.group(3));
            b.cpuRealSec = Double.parseDouble(cpuMatcher.group(4));
            return;
        }

        if (!isGcPauseLine(tags, message)) {
            return;
        }

        Matcher pauseMatcher = GC_PAUSE.matcher(message);
        if (!pauseMatcher.find()) {
            return;
        }

        int gcId = Integer.parseInt(pauseMatcher.group(1));
        String pauseDescription = pauseMatcher.group(2).trim();
        long before = MemorySizeParser.parseToBytes(pauseMatcher.group(3));
        long after = MemorySizeParser.parseToBytes(pauseMatcher.group(4));
        long capacity = MemorySizeParser.parseToBytes(pauseMatcher.group(5));
        double pauseMs = Double.parseDouble(pauseMatcher.group(6));

        PauseClassification classification = classifyPause(pauseDescription);
        GcEventBuilder eventBuilder = builder(builders, gcId, uptimeSeconds, timestamp);

        if (capacity > 0 && (model.getHeapInfo() == null || model.getHeapInfo().heapCapacityBytes() == 0)) {
            mergeHeapInfo(model, 1024L * 1024L, capacity, null, null, "G1");
        }

        GcPauseEvent event = eventBuilder.toPauseEvent(
                classification.pauseType(),
                classification.reason(),
                before,
                after,
                capacity > 0 ? capacity : defaultCapacity(model),
                pauseMs
        );
        model.addPauseEvent(event);

        eventBuilder.phaseTimingsMs.clear();
        eventBuilder.workers = null;
        eventBuilder.cpuUserSec = null;
        eventBuilder.cpuSysSec = null;
        eventBuilder.cpuRealSec = null;
    }

    private void applyRegionLines(String message, Map<Integer, GcEventBuilder> builders,
                                  double uptime, Instant timestamp) {
        Matcher eden = EDEN_REGIONS.matcher(message);
        if (eden.find()) {
            GcEventBuilder b = builder(builders, Integer.parseInt(eden.group(1)), uptime, timestamp);
            b.edenBefore = Integer.parseInt(eden.group(2));
            b.edenAfter = Integer.parseInt(eden.group(3));
            b.edenCap = Integer.parseInt(eden.group(4));
        }
        Matcher survivor = SURVIVOR_REGIONS.matcher(message);
        if (survivor.find()) {
            GcEventBuilder b = builder(builders, Integer.parseInt(survivor.group(1)), uptime, timestamp);
            b.survivorBefore = Integer.parseInt(survivor.group(2));
            b.survivorAfter = Integer.parseInt(survivor.group(3));
            b.survivorCap = Integer.parseInt(survivor.group(4));
        }
        Matcher old = OLD_REGIONS.matcher(message);
        if (old.find()) {
            GcEventBuilder b = builder(builders, Integer.parseInt(old.group(1)), uptime, timestamp);
            b.oldBefore = Integer.parseInt(old.group(2));
            b.oldAfter = Integer.parseInt(old.group(3));
        }
        Matcher humongous = HUMONGOUS_REGIONS.matcher(message);
        if (humongous.find()) {
            GcEventBuilder b = builder(builders, Integer.parseInt(humongous.group(1)), uptime, timestamp);
            b.humongousBefore = Integer.parseInt(humongous.group(2));
            b.humongousAfter = Integer.parseInt(humongous.group(3));
        }
    }

    private void mergeHeapInfo(GcLogModel model, long regionSizeBytes, long capacityBytes,
                               String address, String oopsMode, String gcType) {
        HeapInfo existing = model.getHeapInfo();
        long region = regionSizeBytes > 0 ? regionSizeBytes
                : existing != null ? existing.regionSizeBytes() : 1024L * 1024L;
        long capacity = capacityBytes > 0 ? capacityBytes
                : existing != null ? existing.heapCapacityBytes() : 0;
        String addr = address != null ? address : existing != null ? existing.heapAddress() : null;
        String oops = oopsMode != null ? oopsMode : existing != null ? existing.compressedOopsMode() : null;
        String gc = gcType != null ? gcType : existing != null ? existing.gcType() : "G1";
        model.setHeapInfo(new HeapInfo(region, capacity, gc, addr, oops));
    }

    private static boolean isGcPauseLine(String tags, String message) {
        if (!message.startsWith("GC(") || !message.contains("->") || !message.endsWith("ms")) {
            return false;
        }
        String normalizedTags = tags.replace(" ", "");
        return normalizedTags.equals("gc")
                || normalizedTags.startsWith("gc,") && !normalizedTags.contains("start")
                && !normalizedTags.contains("phases")
                && !normalizedTags.contains("heap")
                && !normalizedTags.contains("cpu")
                && !normalizedTags.contains("task")
                && !normalizedTags.contains("metaspace")
                && !normalizedTags.contains("marking");
    }

    private static long defaultCapacity(GcLogModel model) {
        if (model.getHeapInfo() != null && model.getHeapInfo().heapCapacityBytes() > 0) {
            return model.getHeapInfo().heapCapacityBytes();
        }
        return 0;
    }

    private static Instant parseTimestamp(String text) {
        try {
            return OffsetDateTime.parse(text, GC_LOG_TIMESTAMP).toInstant();
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
            } catch (DateTimeParseException ignoredAgain) {
                return Instant.EPOCH;
            }
        }
    }

    /** JDK unified GC logs use {@code -0400}; ISO_OFFSET_DATE_TIME expects {@code -04:00}. */
    private static final DateTimeFormatter GC_LOG_TIMESTAMP = new java.time.format.DateTimeFormatterBuilder()
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HHMM", "Z")
            .toFormatter();

    static PauseClassification classifyPause(String description) {
        String reason = "";
        String pauseType = description;

        int lastParen = description.lastIndexOf('(');
        if (lastParen > 0 && description.endsWith(")")) {
            reason = description.substring(lastParen + 1, description.length() - 1).trim();
            pauseType = description.substring(0, lastParen).trim();
            int secondParen = pauseType.lastIndexOf('(');
            if (secondParen > 0 && pauseType.endsWith(")")) {
                reason = pauseType.substring(secondParen + 1, pauseType.length() - 1).trim()
                        + " / " + reason;
                pauseType = pauseType.substring(0, secondParen).trim();
            }
        }

        if (pauseType.startsWith("Pause ")) {
            pauseType = pauseType.substring("Pause ".length());
        }

        if (reason.isBlank()) {
            reason = pauseType;
        }

        return new PauseClassification(pauseType, reason);
    }

    record PauseClassification(String pauseType, String reason) {
    }

    private static GcEventBuilder builder(Map<Integer, GcEventBuilder> builders, int gcId,
                                          double uptime, Instant timestamp) {
        GcEventBuilder b = builders.computeIfAbsent(gcId, id -> {
            GcEventBuilder created = new GcEventBuilder();
            created.gcId = id;
            return created;
        });
        b.uptimeSeconds = uptime;
        b.timestamp = timestamp;
        return b;
    }
}
