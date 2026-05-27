package com.gcviewer.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class LogFileFormat {
    private static final DateTimeFormatter MODIFIED =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private LogFileFormat() {
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    public static String formatModified(Instant instant) {
        if (instant == null) {
            return "-";
        }
        return MODIFIED.format(instant);
    }

    public static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (Exception e) {
            return -1;
        }
    }

    public static Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (Exception e) {
            return null;
        }
    }
}
