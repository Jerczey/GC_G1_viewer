package com.gcviewer.model;

public record GenerationMemoryStats(
        String generation,
        long allocatedBytes,
        long peakBytes,
        boolean allocatedKnown
) {
    public static GenerationMemoryStats unknown(String generation) {
        return new GenerationMemoryStats(generation, 0, 0, false);
    }
}
