package com.example.mdt.infrastructure.adapter.backlog;

import com.example.mdt.domain.model.Scan;
import com.example.mdt.domain.usecase.ProcessScanUseCase;
import com.example.mdt.infrastructure.adapter.db.DbHealthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Component
public class BacklogDrainService {
    private static final Logger log = LoggerFactory.getLogger(BacklogDrainService.class);

    private final BacklogStore store;
    private final DbHealthService dbHealth;
    private final ProcessScanUseCase useCase;
    private final ObjectMapper mapper = new ObjectMapper();

    public BacklogDrainService(BacklogStore store, DbHealthService dbHealth, ProcessScanUseCase useCase) {
        this.store = store;
        this.dbHealth = dbHealth;
        this.useCase = useCase;
    }

    @Scheduled(fixedDelayString = "${backlog.drain-interval-ms:5000}")
    public void drain() {
        if (!store.isEnabled()) return;
        if (!dbHealth.isAvailable()) return;

        List<Path> files = store.listOldestFirst(50);
        if (files.isEmpty()) return;

        for (Path p : files) {
            try {
                String body = store.read(p);
                JsonNode root = mapper.readTree(body);
                String dt = root.path("DATATYPE").asText(null);
                if (dt == null || !"SCAN".equalsIgnoreCase(dt)) {
                    log.warn("Skipping backlog file (not SCAN): {}", p.getFileName());
                    store.delete(p);
                    continue;
                }
                JsonNode obj = root.path("OBJECT");
                if (obj.isMissingNode() || obj.isNull() || !obj.has("CSN")) {
                    log.warn("Skipping backlog file (invalid OBJECT): {}", p.getFileName());
                    store.delete(p);
                    continue;
                }
                String stage = obj.path("STAGE").asText(null);
                String device = obj.path("DEVICE").asText(null);
                String machine = obj.path("MACHINE").asText(null);
                List<String> csn = new ArrayList<>();
                if (obj.get("CSN").isArray()) {
                    obj.get("CSN").forEach(n -> csn.add(n.asText()));
                }

                Scan scan = new Scan("SCAN", stage, device, machine, csn);
                int inserted = useCase.process(scan);
                log.info("Backlog drained: {} inserted from {}", inserted, p.getFileName());
                store.delete(p);
            } catch (Exception e) {
                log.warn("Backlog processing failed for {}: {}", p.getFileName(), e.getMessage());
                // leave file to retry later
            }
        }
    }
}
