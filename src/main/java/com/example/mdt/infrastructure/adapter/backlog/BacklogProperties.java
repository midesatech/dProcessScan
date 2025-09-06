package com.example.mdt.infrastructure.adapter.backlog;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "backlog")
public class BacklogProperties {
    private boolean enabled = true;
    private String dir = "data/backlog";
    private int maxPerCycle = 50;
    private long drainIntervalMs = 5000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDir() { return dir; }
    public void setDir(String dir) { this.dir = dir; }

    public int getMaxPerCycle() { return maxPerCycle; }
    public void setMaxPerCycle(int maxPerCycle) { this.maxPerCycle = maxPerCycle; }

    public long getDrainIntervalMs() { return drainIntervalMs; }
    public void setDrainIntervalMs(long drainIntervalMs) { this.drainIntervalMs = drainIntervalMs; }
}
