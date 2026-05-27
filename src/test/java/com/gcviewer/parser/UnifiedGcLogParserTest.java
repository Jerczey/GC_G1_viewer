package com.gcviewer.parser;

import com.gcviewer.model.GcLogModel;
import com.gcviewer.model.GcMetrics;
import com.gcviewer.model.GcPauseEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedGcLogParserTest {

  private final UnifiedGcLogParser parser = new UnifiedGcLogParser();
  private final Path sample = Path.of("gc-2026-05-27_12-20-09.log");

  @Test
  void parsesRhSsoG1Log() throws Exception {
    GcLogModel model = parser.parseFile(sample);
    assertNotNull(model.getHeapInfo());
    assertTrue(model.getHeapInfo().heapCapacityBytes() > 0);
    assertFalse(model.getPauseEvents().isEmpty());

    GcPauseEvent first = model.getPauseEvents().get(0);
    assertEquals(0, first.gcId());
    assertTrue(first.pauseType().contains("Young"));
    assertTrue(first.pauseMs() > 0);
    assertTrue(first.heapBeforeBytes() > first.heapAfterBytes());
    assertNotEquals(Instant.EPOCH, first.timestamp());
    assertTrue(first.timestamp().toString().startsWith("2026-05-27"));
  }

  @Test
  void calculatesMetrics() throws Exception {
    GcLogModel model = parser.parseFile(sample);
    GcMetrics metrics = model.getMetrics();
    assertTrue(metrics.gcEventCount() >= 10);
    assertTrue(metrics.totalGcPauseMs() > 0);
    assertTrue(metrics.maxGcPauseMs() >= metrics.avgGcPauseMs());
    assertFalse(metrics.pauseMsByReason().isEmpty());
  }

  @Test
  void classifiesPauseReasons() {
    var c = UnifiedGcLogParser.classifyPause("Pause Young (Normal) (G1 Evacuation Pause)");
    assertEquals("Young", c.pauseType());
    assertTrue(c.reason().contains("G1 Evacuation Pause"));

    var remark = UnifiedGcLogParser.classifyPause("Pause Remark");
    assertEquals("Remark", remark.pauseType());
  }
}
