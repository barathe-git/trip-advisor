# Travel Advisor Scheduler Configuration

## Overview
The Travel Advisor application now includes a built-in scheduler that automatically syncs all travel advisories at configurable intervals. This guide explains the configuration and best practices.

## Features

✅ **Automatic Sync Every 5 Minutes** - Keeps travel advisor data up-to-date
✅ **Batch Processing** - Efficiently handles millions of records without memory overflow
✅ **Configurable Intervals** - Adjust timing to your needs
✅ **Timezone Support** - Log timestamps in your preferred timezone
✅ **Health Monitoring** - Built-in health check task
✅ **Error Handling** - Graceful error handling with detailed logging
✅ **Enable/Disable** - Toggle scheduler on/off without code changes

---

## Configuration

### YAML Configuration (application.yaml)

```yaml
app:
  scheduler:
    enabled: true                    # Enable/disable scheduling
    sync-all-interval-ms: 300000     # Sync interval in milliseconds (5 minutes)
    initial-delay-ms: 30000          # Initial delay before first sync (30 seconds)
    timezone: UTC                    # Timezone for logging timestamps
    batch-size: 5                    # Batch size for large datasets (5 for testing, 100-1000 for production)
```

### Configuration Properties Explained

| Property | Default | Description |
|----------|---------|-------------|
| `enabled` | `true` | Enable or disable the scheduler completely |
| `sync-all-interval-ms` | `300000` | Time (in ms) between successive sync executions. 300000 = 5 minutes |
| `initial-delay-ms` | `30000` | Time (in ms) to wait before starting the first sync. 30000 = 30 seconds |
| `timezone` | `UTC` | Timezone for formatted timestamps in logs |
| `batch-size` | `5` | Number of cities to process per batch. Use 5-10 for testing, 100-1000 for production with large datasets |

### Common Interval Configurations

```yaml
# Every 1 minute
sync-all-interval-ms: 60000

# Every 5 minutes (DEFAULT)
sync-all-interval-ms: 300000

# Every 10 minutes
sync-all-interval-ms: 600000

# Every 30 minutes
sync-all-interval-ms: 1800000

# Every 1 hour
sync-all-interval-ms: 3600000

# Every 6 hours
sync-all-interval-ms: 21600000

# Every 24 hours
sync-all-interval-ms: 86400000
```

### Common Timezone Values

```yaml
timezone: UTC              # Coordinated Universal Time
timezone: America/New_York # Eastern Time
timezone: America/Los_Angeles # Pacific Time
timezone: Europe/London    # London Time
timezone: Europe/Paris     # Central European Time
timezone: Asia/Tokyo       # Japan Time
timezone: Asia/Kolkata     # India Standard Time
timezone: Australia/Sydney # Sydney Time
```

---

## How It Works

### Execution Flow

1. **Application Startup**
   - `AdvisoryScheduler` component is instantiated
   - Initial delay of 30 seconds begins
   
2. **First Sync Execution** (after 30 seconds)
   - Calls `AdvisoryService.refreshAllCitiesInBatches(batchSize, concurrency)`
   - Fetches all unique cities from the database
   - Processes cities in configured batches (default: 5 cities per batch)
   - Each batch is processed sequentially to control memory usage
   - Within each batch, up to 5 cities are synced concurrently
   - Each city is updated with current weather and country data
   - Audit type (CREATED/UPDATED) is tracked
   
3. **Subsequent Syncs** (every 5 minutes)
   - Same batch processing repeats on fixed interval
   - Uses `fixedDelay` to ensure consistent spacing between completions
   
4. **Health Check** (every 1 minute, starting after 10 seconds)
   - Debug-level logging confirms scheduler is active
   - Helps identify if scheduler stops unexpectedly

### Batch Processing Benefits

**For Large Datasets (Millions of Records)**:
- ✅ **Memory Efficient**: Processes data in chunks, preventing memory overflow
- ✅ **Database Friendly**: Reduces lock contention with sequential batch processing
- ✅ **API Rate Limiting**: Controlled concurrency prevents overwhelming external APIs
- ✅ **Progress Tracking**: Batch-level logging shows processing progress
- ✅ **Fault Tolerance**: Failure in one batch doesn't prevent subsequent batches

**Example with 1,000,000 Cities**:
```
Batch Size 5, Concurrency 5:
- Total batches: 200,000
- Each batch takes ~2-3 seconds (5 API calls in parallel)
- Total time: ~400,000-600,000 seconds (5-7 days per full cycle)
- Memory peak: Only 5 cities in memory at once (vs all 1,000,000)

Batch Size 100, Concurrency 5:
- Total batches: 10,000
- Each batch takes ~4-5 seconds (100 cities, 5 concurrent)
- Total time: ~40,000-50,000 seconds (11-14 hours per full cycle)
- Better throughput with maintained memory efficiency
```

### Task Execution Details

```
Time    | Event
--------|--------------------------------------------------
  0s    | Application starts
 10s    | Health check starts (runs every 60s)
 30s    | First batch sync-all starts → fetches all cities in batches
 35s    | (approx) First batch sync-all completes
 5:35   | Second batch sync-all starts (5 minutes later)
10:35   | Third batch sync-all starts
...     | Pattern continues
```

---

## Task Methods

### syncAll()
**Triggered**: Every 5 minutes (configurable)
- Syncs all travel advisories in the database using batch processing
- Runs `AdvisoryService.refreshAllCitiesInBatches(batchSize, concurrency)` in non-blocking manner
- Processes cities in batches to handle large datasets efficiently
- Logs success count and any errors per batch
- Uses reactive streams with `.count()` to determine sync count

**Key Features**:
- ✅ Batch processing with configurable batch size
- ✅ Controlled concurrency within and between batches
- ✅ Non-blocking execution using Project Reactor
- ✅ Comprehensive error logging per batch
- ✅ Memory-efficient for large datasets
- ✅ Completion tracking with progress indication

**Batch Processing Details**:
```
Configuration:
- batch-size: 5 (default, configurable)
- concurrency: 5 (within batch)
- batch-order: Sequential (one batch after another)

Processing Flow:
1. Fetch all unique cities from database
2. Group cities into batches of N size
3. For each batch:
   a. Process up to 5 cities concurrently
   b. Log batch completion
   c. Continue to next batch
4. Track total synced advisories across all batches
```

### healthCheck()
**Triggered**: Every 60 seconds (starting after 10 seconds)
- Verifies scheduler is still active
- Debug-level logging (use with `-Ddebug` or logging level DEBUG)
- Helps detect scheduler hangs or failures

---

## Monitoring & Logging

### Log Levels

**INFO Level** (Always visible):
```
[SCHEDULER] Starting sync-all task at: 2026-02-06T14:42:30.123 UTC
[SCHEDULER] Successfully synced 5 advisories at: 2026-02-06T14:42:35.456 UTC
[SCHEDULER] Error during sync-all task at: 2026-02-06T14:42:40.789 UTC, error: Connection timeout
```

**DEBUG Level** (Use with debug flag):
```
[SCHEDULER] Health check - Scheduler is active at: 2026-02-06T14:43:00.000 UTC
[SCHEDULER] Sync-all task completed at: 2026-02-06T14:42:40.789 UTC
```

### Sample Log Output

**With Batch Processing (5 items per batch)**:
```
2026-02-06 14:42:30.123 [SCHEDULER] Starting batch sync-all task at: 2026-02-06T14:42:30.123 UTC
2026-02-06 14:42:30.456 [SERVICE] Starting batch refresh - batch size: 5, concurrency: 5
2026-02-06 14:42:30.789 [SERVICE] Processing batch of 5 cities
2026-02-06 14:42:32.123 [SERVICE] Batch of 5 cities completed
2026-02-06 14:42:32.456 [SERVICE] Processing batch of 5 cities
2026-02-06 14:42:34.789 [SERVICE] Batch of 5 cities completed
2026-02-06 14:42:35.123 [SERVICE] Completed batch refresh of all advisories
2026-02-06 14:42:35.456 [SCHEDULER] Successfully synced 10 advisories in batches at: 2026-02-06T14:42:35.456 UTC
2026-02-06 14:43:00.000 [SCHEDULER] Health check - Scheduler is active at: 2026-02-06T14:43:00.000 UTC
2026-02-06 14:45:30.111 [SCHEDULER] Starting batch sync-all task at: 2026-02-06T14:45:30.111 UTC
2026-02-06 14:45:30.234 [SERVICE] Starting batch refresh - batch size: 5, concurrency: 5
2026-02-06 14:45:30.567 [SERVICE] Processing batch of 5 cities
2026-02-06 14:45:32.890 [SERVICE] Batch of 5 cities completed
2026-02-06 14:45:33.456 [SCHEDULER] Successfully synced 5 advisories in batches at: 2026-02-06T14:45:33.456 UTC
```

---

## Disabling the Scheduler

To disable the scheduler without code changes, set in your YAML:

```yaml
app:
  scheduler:
    enabled: false
```

Or use environment variable:
```bash
export APP_SCHEDULER_ENABLED=false
java -jar traveladvisor.jar
```

Or command-line argument:
```bash
java -jar traveladvisor.jar --app.scheduler.enabled=false
```

---

## Performance Considerations

### Memory & Database Impact

- **Concurrency**: Default is 5 concurrent cities per refresh
- **Duration**: Each sync typically takes 5-10 seconds depending on:
  - Number of stored cities
  - External API response times
  - Network latency
- **Interval**: 5 minutes allows sufficient time for sync completion before next execution

### Recommendations

| Scenario | Interval | Reason |
|----------|----------|--------|
| Development | 5-10 minutes | Balances testing with server load |
| Small Deployment (< 100 cities) | 5 minutes | ✓ DEFAULT - Good balance |
| Medium Deployment (100-500 cities) | 10-15 minutes | Prevents overlapping syncs |
| Large Deployment (> 500 cities) | 30+ minutes | Reduce database/API load |
| High Availability | 1 minute | Keep data very fresh |

---

## Spring Configuration Details

### AdvisoryScheduler Component
- **Package**: `org.pyt.traveladvisor.config`
- **Annotations**: 
  - `@Component` - Registered as Spring bean
  - `@ConditionalOnProperty` - Only active if `app.scheduler.enabled=true`
  - `@Scheduled` - Enables scheduling on methods

### SchedulerProperties Component
- **Package**: `org.pyt.traveladvisor.config`
- **Annotations**: 
  - `@ConfigurationProperties(prefix = "app.scheduler")` - Binds YAML config
  - `@Component` - Registered as Spring bean

### Application Main Class
- `@EnableScheduling` - Enables Spring's scheduling support
- Required for `@Scheduled` methods to work

---

## Batch Size Tuning Guide

### Testing Configuration (Default)
```yaml
app:
  scheduler:
    batch-size: 5
    sync-all-interval-ms: 300000  # 5 minutes
```
**Use Case**: Development, testing, small datasets (< 1,000 cities)
**Characteristics**:
- Fast iteration cycles
- Easy to observe batch boundaries in logs
- Memory usage: ~50MB per batch
- Good for debugging batch processing

### Small Production (1,000 - 10,000 Cities)
```yaml
app:
  scheduler:
    batch-size: 50
    sync-all-interval-ms: 600000  # 10 minutes
```
**Characteristics**:
- 20-200 batches total
- ~2-5 batches per sync cycle
- Memory usage: ~500MB per batch
- Balanced throughput and memory

### Large Production (10,000 - 100,000 Cities)
```yaml
app:
  scheduler:
    batch-size: 200
    sync-all-interval-ms: 1800000  # 30 minutes
```
**Characteristics**:
- 50-500 batches total
- ~2-3 batches per sync cycle
- Memory usage: ~2GB per batch
- Higher throughput, requires tuning

### Enterprise (100,000+ Cities - Millions)
```yaml
app:
  scheduler:
    batch-size: 500
    sync-all-interval-ms: 3600000  # 1 hour
```
**Characteristics**:
- 200+ batches (for millions of cities)
- Process multiple times per hour or per 24 hours
- Memory usage: ~5GB per batch
- Requires load balancing and monitoring

### Tuning Formula
```
Optimal Batch Size = (Available Memory MB × 0.3) / (Memory per City MB)

Example:
- Total Available Memory: 8GB
- Memory per City: ~1MB
- Optimal Size = (8000 × 0.3) / 1 = 2400 cities per batch

For safety, use 50% of calculated: batch-size: 1200
```

### Monitoring Batch Performance

**Check logs for these metrics**:
```
[SERVICE] Processing batch of N cities          ← Batch start
[SERVICE] Batch of N cities completed          ← Batch end
[SCHEDULER] Successfully synced X advisories    ← Total count
```

**Calculate stats**:
- Batch Duration = End time - Start time
- Total Batches = Total Cities / Batch Size
- Estimated Full Sync = Batch Duration × Total Batches

---

## Advanced Usage

### Custom Scheduling via Environment Variables

```bash
# Run with custom 10-minute interval
java -jar traveladvisor.jar \
  --app.scheduler.sync-all-interval-ms=600000 \
  --app.scheduler.initial-delay-ms=15000

# Run with custom timezone
java -jar traveladvisor.jar \
  --app.scheduler.timezone=America/New_York
```

### Spring Profile-based Configuration

**application-dev.yaml**:
```yaml
app:
  scheduler:
    sync-all-interval-ms: 60000      # 1 minute for dev
    initial-delay-ms: 5000            # Quick start
```

**application-prod.yaml**:
```yaml
app:
  scheduler:
    sync-all-interval-ms: 300000     # 5 minutes for prod
    initial-delay-ms: 30000           # Wait for app warmup
```

Run with profile:
```bash
java -jar traveladvisor.jar --spring.profiles.active=prod
```

---

## Troubleshooting

### Scheduler Not Running

**Check 1**: Is scheduling enabled?
```yaml
app.scheduler.enabled: true
```

**Check 2**: Is `@EnableScheduling` in main class?
```java
@EnableScheduling
@SpringBootApplication
public class TravelAdvisoryApplication { ... }
```

**Check 3**: Enable DEBUG logging:
```yaml
logging:
  level:
    org.pyt.traveladvisor: DEBUG
```

### Sync Taking Too Long

1. Increase interval to reduce overlap
2. Check external API response times
3. Monitor database query performance
4. Review error logs for failed syncs

### Timezone Issues

Ensure timezone is valid IANA format:
```bash
# Valid
timezone: America/New_York

# Invalid (won't work)
timezone: EST
timezone: Eastern Time
```

---

## Implementation Classes

### AdvisoryScheduler.java
Core scheduler component with `@Scheduled` methods.

**Methods**:
- `syncAll()` - Main scheduling task
- `healthCheck()` - Health monitoring task
- `getFormattedTimestamp()` - Helper for formatted logging

### SchedulerProperties.java
Configuration properties holder for scheduler settings.

**Fields**:
- `enabled` - Toggle scheduler on/off
- `syncAllIntervalMs` - Interval in milliseconds
- `initialDelayMs` - Initial delay in milliseconds
- `timezone` - Timezone for logging

---

## Complete Example Configuration

```yaml
server:
  port: 8080

spring:
  application:
    name: traveladvisor
  data:
    mongodb:
      uri: mongodb://localhost:27017/travel

app:
  security:
    bearer-token: my-secret-token
  sync:
    multi-city-audit: true
  scheduler:
    enabled: true
    sync-all-interval-ms: 300000     # 5 minutes
    initial-delay-ms: 30000          # 30 seconds
    timezone: UTC

external:
  weather:
    base-url: https://api.openweathermap.org
    api-key: token
    timeout-ms: 5000
  country:
    base-url: https://restcountries.com
    timeout-ms: 5000
  cities:
    base-url: http://api.geonames.org
    username: username
    top-n: 5
    concurrency: 5

logging:
  level:
    org.pyt.traveladvisor: INFO
```

---

## Summary

✅ **Setup Complete** - Scheduler automatically syncs all advisories every 5 minutes
✅ **Fully Configurable** - Adjust interval, delay, and timezone in YAML
✅ **Production Ready** - Includes error handling, logging, and health monitoring
✅ **Zero Dependencies** - Uses Spring's built-in scheduling (no additional libraries needed)
✅ **Easy to Disable** - Single configuration flag to turn off

No additional code changes required - just configure and run! 🚀

