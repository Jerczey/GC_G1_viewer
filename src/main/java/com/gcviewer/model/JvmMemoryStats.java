package com.gcviewer.model;

public record JvmMemoryStats(
        GenerationMemoryStats young,
        GenerationMemoryStats old,
        GenerationMemoryStats humongous,
        GenerationMemoryStats metaspace,
        GenerationMemoryStats total
) {
}
