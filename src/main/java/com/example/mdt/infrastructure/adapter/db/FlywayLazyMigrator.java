package com.example.mdt.infrastructure.adapter.db;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class FlywayLazyMigrator {
    private static final Logger log = LoggerFactory.getLogger(FlywayLazyMigrator.class);

    private final DataSource dataSource;
    private final String locations;
    private final boolean baselineOnMigrate;
    private final AtomicBoolean ran = new AtomicBoolean(false);

    public FlywayLazyMigrator(
            DataSource dataSource,
            @Value("${flyway.locations:classpath:db/migration}") String locations,
            @Value("${flyway.lazy.baseline-on-migrate:false}") boolean baselineOnMigrate
    ) {
        this.dataSource = dataSource;
        this.locations = locations;
        this.baselineOnMigrate = baselineOnMigrate;
    }

    public void migrate() {
        if (ran.get()) return;
        synchronized (this) {
            if (ran.get()) return;
            log.info("Running Flyway migrations lazily (locations={})", locations);
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    .baselineOnMigrate(baselineOnMigrate)
                    .load();
            flyway.migrate();
            ran.set(true);
            log.info("Flyway migrations completed");
        }
    }
}
