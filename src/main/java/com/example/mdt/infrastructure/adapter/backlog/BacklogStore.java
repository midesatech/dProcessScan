package com.example.mdt.infrastructure.adapter.backlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class BacklogStore {
    private static final Logger log = LoggerFactory.getLogger(BacklogStore.class);

    private final BacklogProperties props;
    private final Path dir;

    public BacklogStore(BacklogProperties props) {
        this.props = props;
        this.dir = Paths.get(props.getDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("Could not create backlog directory {}: {}", dir, e.getMessage());
        }
    }

    public boolean isEnabled() { return props.isEnabled(); }

    public Path enqueue(String payload, String reason) {
        try {
            String name = String.format("%d_%s_%s.json", Instant.now().toEpochMilli(), reason, UUID.randomUUID());
            Path p = dir.resolve(name);
            Files.writeString(p, payload, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            return p;
        } catch (Exception e) {
            log.error("Failed to write backlog file: {}", e.getMessage());
            return null;
        }
    }

    public List<Path> listOldestFirst(int limit) {
        try (Stream<Path> st = Files.list(dir)) {
            return st.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(this::fileTime))
                    .limit(limit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private long fileTime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return Long.MAX_VALUE;
        }
    }

    public String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    public void delete(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }
}
