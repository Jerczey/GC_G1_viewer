package com.gcviewer.parser;

import com.gcviewer.model.GcLogModel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class GcLogTailer implements AutoCloseable {
    private final Path path;
    private final UnifiedGcLogParser parser = new UnifiedGcLogParser();
    private RandomAccessFile file;
    private long position;
    private boolean active = true;

    public GcLogTailer(Path path) {
        this.path = path;
    }

    public void start(Consumer<GcLogModel> onUpdate) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("GC log file not found: " + path);
        }
        file = new RandomAccessFile(path.toFile(), "r");
        position = 0;
        file.seek(position);

        GcLogModel model = parser.parseFile(path);
        onUpdate.accept(model);
        position = file.length();
    }

    public boolean poll(Consumer<GcLogModel> onUpdate) throws IOException {
        if (!active || file == null) {
            return false;
        }

        long length = file.length();
        if (length <= position) {
            return false;
        }

        file.seek(position);
        StringBuilder chunk = new StringBuilder();
        String line;
        while ((line = file.readLine()) != null) {
            if (!line.isEmpty()) {
                chunk.append(line).append('\n');
            }
        }
        position = file.getFilePointer();

        if (chunk.isEmpty()) {
            return false;
        }

        GcLogModel incremental = new GcLogModel(path);
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(chunk.toString()))) {
            parser.parse(reader, incremental);
        }

        GcLogModel merged = parser.parseFile(path);
        onUpdate.accept(merged);
        return true;
    }

    public void stop() {
        active = false;
    }

    @Override
    public void close() throws IOException {
        active = false;
        if (file != null) {
            file.close();
            file = null;
        }
    }
}
