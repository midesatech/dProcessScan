package com.example.mdt.infrastructure.adapter.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DbHealthService {
    private static final Logger log = LoggerFactory.getLogger(DbHealthService.class);

    private final DataSource dataSource;
    private final AtomicBoolean available = new AtomicBoolean(false);
    private final FlywayLazyMigrator migrator;
    private final AtomicBoolean migrated = new AtomicBoolean(false);

    public DbHealthService(DataSource dataSource, FlywayLazyMigrator migrator) {
        this.dataSource = dataSource;
        this.migrator = migrator;
    }

    public boolean isAvailable() {
        return available.get();
    }

    @Scheduled(fixedDelayString = "${db.health.check-interval-ms:5000}")
    public void check() {
        boolean ok = false;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1")) {
            ps.execute();
            ok = true;
        } catch (Exception ex) {
            ok = false;
            if (available.get()) {
                log.warn("DB became unavailable: {}", ex.getMessage());
            } else {
                log.debug("DB still unavailable: {}", ex.getMessage());
            }
        }
        boolean prev = available.getAndSet(ok);
        if (!prev && ok) {
            log.info("DB is now AVAILABLE");
            if (!migrated.get()) {
                try {
                    migrator.migrate();
                    migrated.set(true);
                } catch (Exception e) {
                    log.error("Flyway migration failed (will retry next check): {}", e.getMessage());
                    migrated.set(false);
                }
            }
        }
        if (prev && !ok) {
            log.warn("DB is now UNAVAILABLE");
        }
    }
}
