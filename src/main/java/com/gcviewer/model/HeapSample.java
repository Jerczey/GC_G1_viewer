package com.gcviewer.model;

public record HeapSample(double uptimeSeconds, long usedBytes, long capacityBytes, boolean isAfterGc) {
}
