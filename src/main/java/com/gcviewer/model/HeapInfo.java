package com.gcviewer.model;

public record HeapInfo(
        long regionSizeBytes,
        long heapCapacityBytes,
        String gcType,
        String heapAddress,
        String compressedOopsMode
) {
    public HeapInfo(long regionSizeBytes, long heapCapacityBytes, String gcType) {
        this(regionSizeBytes, heapCapacityBytes, gcType, null, null);
    }

    public double heapCapacityMb() {
        return heapCapacityBytes / (1024.0 * 1024.0);
    }

    public double regionSizeMb() {
        return regionSizeBytes / (1024.0 * 1024.0);
    }
}
