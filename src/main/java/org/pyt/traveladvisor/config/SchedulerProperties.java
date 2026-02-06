package org.pyt.traveladvisor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.scheduler")
public class SchedulerProperties {
    private boolean enabled = true;
    private long syncAllIntervalMs = 300000; // 5 minutes default
    private long initialDelayMs = 30000;      // 30 seconds default
    private String timezone = "UTC";
    private int batchSize = 5;                // Batch size for processing large datasets (testing: 5, production: 100-1000)
}

