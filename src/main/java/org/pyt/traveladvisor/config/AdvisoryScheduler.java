package org.pyt.traveladvisor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pyt.traveladvisor.service.AdvisoryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Scheduled task component for syncing all travel advisories.
 * <p>
 * Configuration:
 * - app.scheduler.enabled: Enable/disable scheduling (default: true)
 * - app.scheduler.sync-all-interval-ms: Interval between syncs in milliseconds (default: 300000 = 5 minutes)
 * - app.scheduler.initial-delay-ms: Initial delay before first sync in milliseconds (default: 30000 = 30 seconds)
 * - app.scheduler.timezone: Timezone for logging timestamps (default: UTC)
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class AdvisoryScheduler {

    private final AdvisoryService advisoryService;
    private final SchedulerProperties schedulerProperties;

    /**
     * Syncs all travel advisories at fixed intervals using batch processing.
     * Uses fixed delay to ensure consistent interval between task completions.
     * Processes records in batches to handle large datasets efficiently.
     */
    @Scheduled(
            initialDelayString = "${app.scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${app.scheduler.sync-all-interval-ms:300000}"
    )
    public void syncAll() {
        String timestamp = getFormattedTimestamp();
        log.info("[SCHEDULER] Starting batch sync-all task at: {}", timestamp);

        int batchSize = schedulerProperties.getBatchSize();
        int concurrency = 5; // Default concurrency

        advisoryService.refreshAllCitiesInBatches(batchSize, concurrency)
                .count()
                .subscribe(
                        count -> {
                            String completionTime = getFormattedTimestamp();
                            log.info("[SCHEDULER] Successfully synced {} advisories in batches at: {}", count, completionTime);
                        },
                        error -> {
                            String errorTime = getFormattedTimestamp();
                            log.error("[SCHEDULER] Error during batch sync-all task at: {}, error: {}", errorTime, error.getMessage(), error);
                        },
                        () -> {
                            String endTime = getFormattedTimestamp();
                            log.debug("[SCHEDULER] Batch sync-all task completed at: {}", endTime);
                        }
                );
    }

    /**
     * Health check scheduled task - logs scheduler status every minute.
     */
    @Scheduled(fixedRate = 60000, initialDelay = 10000)
    public void healthCheck() {
        String timestamp = getFormattedTimestamp();
        log.debug("[SCHEDULER] Health check - Scheduler is active at: {}", timestamp);
    }

    /**
     * Get formatted timestamp in configured timezone.
     */
    private String getFormattedTimestamp() {
        ZoneId zoneId = ZoneId.of(schedulerProperties.getTimezone());
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS Z"));
    }
}

