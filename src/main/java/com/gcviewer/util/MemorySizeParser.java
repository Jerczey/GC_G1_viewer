package com.gcviewer.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MemorySizeParser {
    private static final Pattern SIZE = Pattern.compile("([\\d.]+)\\s*([KMG])", Pattern.CASE_INSENSITIVE);

    private MemorySizeParser() {
    }

    public static long parseToBytes(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        Matcher matcher = SIZE.matcher(text.trim());
        if (!matcher.find()) {
            return 0;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toUpperCase();
        return switch (unit) {
            case "K" -> Math.round(value * 1024);
            case "M" -> Math.round(value * 1024 * 1024);
            case "G" -> Math.round(value * 1024 * 1024 * 1024);
            default -> Math.round(value);
        };
    }
}
