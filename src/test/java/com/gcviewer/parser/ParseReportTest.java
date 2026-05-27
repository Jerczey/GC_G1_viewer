package com.gcviewer.parser;

import com.gcviewer.model.GcLogModel;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ParseReportTest {

  @Test
  void printsSummaryForAllBundledLogs() throws Exception {
    try (var stream = Files.list(Path.of("."))) {
      var logs = stream.filter(p -> p.getFileName().toString().matches("gc-.*\\.log")).sorted().toList();
      assertFalse(logs.isEmpty(), "Expected bundled gc-*.log sample files");
      UnifiedGcLogParser parser = new UnifiedGcLogParser();
      for (Path log : logs) {
        GcLogModel model = parser.parseFile(log);
        assertTrue(model.getPauseEvents().size() > 0, log.getFileName() + " should contain GC pauses");
        assertTrue(model.getMetrics().totalGcPauseMs() > 0, log.getFileName() + " should have pause time");
        System.out.printf("%s -> %d events, %.1f%% throughput, %.1f ms total pause%n",
            log.getFileName(),
            model.getPauseEvents().size(),
            model.getMetrics().throughputPercent(),
            model.getMetrics().totalGcPauseMs());
      }
    }
  }
}
