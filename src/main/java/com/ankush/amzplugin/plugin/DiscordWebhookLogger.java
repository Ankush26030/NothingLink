package com.ankush.amzplugin.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced Discord Webhook Logger for NothingLink Plugin.
 *
 * Features:
 * - Rate-limited sending (respects Discord 30 req/min webhook limit)
 * - Buffered log queue with auto-flush
 * - Rich embeds with fields, footers, thumbnails
 * - Uptime tracking & session statistics
 * - Graceful flush on shutdown (drains all pending logs)
 * - Track event logging (start, end, error, stuck)
 * - Plugin lifecycle logging (load, register, shutdown)
 */
public class DiscordWebhookLogger {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebhookLogger.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Discord rate limit: 30 requests per minute per webhook
    private static final long MIN_SEND_INTERVAL_MS = 2100; // ~28 req/min to stay safe

    private final String webhookUrl;
    private final String botName;
    private final String avatarUrl;
    private final Instant startTime = Instant.now();

    // Statistics
    private final AtomicLong totalTracksLoaded = new AtomicLong(0);
    private final AtomicLong totalTrackErrors = new AtomicLong(0);
    private final AtomicLong totalSearches = new AtomicLong(0);
    private final AtomicLong totalWebhooksSent = new AtomicLong(0);
    private final AtomicLong totalWebhookErrors = new AtomicLong(0);

    // Queue + rate limiter
    private final BlockingQueue<EmbedPayload> sendQueue = new LinkedBlockingQueue<>(500);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NothingLink-Webhook-Scheduler");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "NothingLink-Webhook-Sender");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean shuttingDown = false;
    private volatile long lastSendTime = 0;
    private boolean searchLoggingEnabled = false;

    // ──────────────────────────────────── Embed colors
    private static final int COLOR_SUCCESS = 0x2ECC71; // Green
    private static final int COLOR_WARNING = 0xF1C40F; // Yellow
    private static final int COLOR_ERROR = 0xE74C3C; // Red
    private static final int COLOR_INFO = 0x3498DB; // Blue
    private static final int COLOR_STARTUP = 0x9B59B6; // Purple
    private static final int COLOR_SHUTDOWN = 0xE67E22; // Orange
    private static final int COLOR_TRACK = 0x1ABC9C; // Teal
    private static final int COLOR_SEARCH = 0x2980B9; // Dark blue
    private static final int COLOR_CRITICAL = 0x992D22; // Dark red

    public DiscordWebhookLogger(String webhookUrl, String botName, String avatarUrl) {
        this.webhookUrl = webhookUrl;
        this.botName = (botName != null && !botName.isBlank()) ? botName : "NothingLink";
        this.avatarUrl = avatarUrl;

        // Start the queue consumer
        sender.submit(this::processQueue);

        log.info("Discord Webhook Logger initialized (bot={}, queue=500)", this.botName);
    }

    public DiscordWebhookLogger(String webhookUrl) {
        this(webhookUrl, "NothingLink", null);
    }

    public void setSearchLoggingEnabled(boolean enabled) {
        this.searchLoggingEnabled = enabled;
    }

    // ════════════════════════════════════════════════════════════════
    // PUBLIC API — High-level logging methods
    // ════════════════════════════════════════════════════════════════

    /**
     * Log plugin startup with full summary.
     */
    public void logStartup(String version, Map<String, Boolean> sources, String javaVersion, String lavalinkVersion) {
        StringBuilder desc = new StringBuilder();
        desc.append("```\n");
        desc.append("┌─────────────────────────────────┐\n");
        desc.append("│     NothingLink Plugin v").append(version).append("     │\n");
        desc.append("│         Starting Up...          │\n");
        desc.append("└─────────────────────────────────┘\n");
        desc.append("```\n");

        List<EmbedField> fields = new ArrayList<>();

        // Source status
        StringBuilder sourceStatus = new StringBuilder();
        for (var entry : sources.entrySet()) {
            sourceStatus.append(entry.getValue() ? "✅" : "❌")
                    .append(" **").append(entry.getKey()).append("**\n");
        }
        fields.add(new EmbedField("🔌 Audio Sources", sourceStatus.toString(), true));

        // System info
        StringBuilder sysInfo = new StringBuilder();
        sysInfo.append("☕ Java: `").append(javaVersion != null ? javaVersion : System.getProperty("java.version"))
                .append("`\n");
        sysInfo.append("🖥️ OS: `").append(System.getProperty("os.name")).append("`\n");
        sysInfo.append("💾 Memory: `").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MB`\n");
        if (lavalinkVersion != null)
            sysInfo.append("🔗 Lavalink: `").append(lavalinkVersion).append("`\n");
        fields.add(new EmbedField("⚙️ System Info", sysInfo.toString(), true));

        // Webhook info
        fields.add(new EmbedField("📡 Webhook", "✅ Connected & Logging", false));

        enqueue(new EmbedPayload(
                "🚀 NothingLink Plugin Started",
                desc.toString(),
                COLOR_STARTUP,
                "NothingLink v" + version + " • Startup",
                fields,
                null));
    }

    /**
     * Log when audio sources are registered with the player manager.
     */
    public void logSourcesRegistered(Map<String, String> sourceDetails) {
        StringBuilder desc = new StringBuilder();
        desc.append("Audio sources have been registered with the player manager.\n\n");

        List<EmbedField> fields = new ArrayList<>();
        for (var entry : sourceDetails.entrySet()) {
            fields.add(new EmbedField("🎶 " + entry.getKey(), entry.getValue(), true));
        }

        enqueue(new EmbedPayload(
                "📡 Audio Sources Registered",
                desc.toString(),
                COLOR_SUCCESS,
                "Source Registration Complete",
                fields,
                null));
    }

    /**
     * Log a track being loaded/started.
     */
    public void logTrackStart(String title, String author, String source, long durationMs, String uri) {
        totalTracksLoaded.incrementAndGet();

        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("🎵 Track", truncate(title, 256), true));
        fields.add(new EmbedField("👤 Artist", truncate(author != null ? author : "Unknown", 256), true));
        fields.add(new EmbedField("📀 Source", source != null ? source : "Unknown", true));
        fields.add(new EmbedField("⏱️ Duration", formatDuration(durationMs), true));

        String desc = uri != null ? "[Open Track](" + uri + ")" : "";

        enqueue(new EmbedPayload(
                "▶️ Track Loaded",
                desc,
                COLOR_TRACK,
                "Tracks loaded: " + totalTracksLoaded.get(),
                fields,
                null));
    }

    /**
     * Log a track ending.
     */
    public void logTrackEnd(String title, String author, String reason) {
        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("🎵 Track", truncate(title, 256), true));
        fields.add(new EmbedField("👤 Artist", truncate(author != null ? author : "Unknown", 256), true));
        fields.add(new EmbedField("📋 Reason", reason != null ? reason : "Unknown", true));

        enqueue(new EmbedPayload(
                "⏹️ Track Ended",
                null,
                COLOR_INFO,
                "Session uptime: " + getUptime(),
                fields,
                null));
    }

    /**
     * Log a track error/exception.
     */
    public void logTrackError(String title, String author, String source, Throwable error) {
        totalTrackErrors.incrementAndGet();

        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("🎵 Track", truncate(title, 256), true));
        fields.add(new EmbedField("👤 Artist", truncate(author != null ? author : "Unknown", 256), true));
        fields.add(new EmbedField("📀 Source", source != null ? source : "Unknown", true));

        String errorMsg = error != null ? error.getMessage() : "Unknown error";
        fields.add(new EmbedField("❌ Error", "```\n" + truncate(errorMsg, 900) + "\n```", false));

        if (error != null) {
            String stackTrace = getStackTrace(error);
            if (!stackTrace.isEmpty()) {
                fields.add(new EmbedField("📜 Stack Trace", "```java\n" + truncate(stackTrace, 900) + "\n```", false));
            }
        }

        enqueue(new EmbedPayload(
                "💥 Track Error",
                null,
                COLOR_ERROR,
                "Total errors: " + totalTrackErrors.get(),
                fields,
                null));
    }

    /**
     * Log a stuck track.
     */
    public void logTrackStuck(String title, String author, long thresholdMs) {
        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("🎵 Track", truncate(title, 256), true));
        fields.add(new EmbedField("👤 Artist", truncate(author != null ? author : "Unknown", 256), true));
        fields.add(new EmbedField("⏰ Threshold", thresholdMs + "ms", true));

        enqueue(new EmbedPayload(
                "⚠️ Track Stuck",
                "Track has been stuck for longer than the threshold — possible stream or network issue.",
                COLOR_WARNING,
                "Session uptime: " + getUptime(),
                fields,
                null));
    }

    /**
     * Log a search query.
     */
    public void logSearch(String query, String source, int resultCount) {
        totalSearches.incrementAndGet();
        if (!searchLoggingEnabled) return;

        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("🔍 Query", "`" + truncate(query, 200) + "`", false));
        fields.add(new EmbedField("📀 Source", source != null ? source : "Unknown", true));
        fields.add(new EmbedField("📊 Results", String.valueOf(resultCount), true));

        enqueue(new EmbedPayload(
                "🔎 Search Performed",
                null,
                COLOR_SEARCH,
                "Total searches: " + totalSearches.get(),
                fields,
                null));
    }

    /**
     * Log a load failure (no match or load error).
     */
    public void logLoadFailed(String identifier, String source, String reason) {
        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("🔗 Identifier", "`" + truncate(identifier, 200) + "`", false));
        fields.add(new EmbedField("📀 Source", source != null ? source : "Unknown", true));
        fields.add(new EmbedField("❌ Reason", reason != null ? reason : "Unknown", true));

        enqueue(new EmbedPayload(
                "⚠️ Load Failed",
                null,
                COLOR_WARNING,
                null,
                fields,
                null));
    }

    /**
     * Log plugin shutdown with session statistics.
     */
    public void logShutdown(String version, String reason) {
        StringBuilder desc = new StringBuilder();
        desc.append("```\n");
        desc.append("┌─────────────────────────────────┐\n");
        desc.append("│     NothingLink Plugin v").append(version).append("     │\n");
        desc.append("│         Shutting Down...        │\n");
        desc.append("└─────────────────────────────────┘\n");
        desc.append("```\n");

        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("⏱️ Uptime", getUptime(), true));
        fields.add(new EmbedField("🎵 Tracks Loaded", String.valueOf(totalTracksLoaded.get()), true));
        fields.add(new EmbedField("❌ Track Errors", String.valueOf(totalTrackErrors.get()), true));
        fields.add(new EmbedField("🔍 Searches", String.valueOf(totalSearches.get()), true));
        fields.add(new EmbedField("📤 Webhooks Sent", String.valueOf(totalWebhooksSent.get()), true));
        fields.add(new EmbedField("📉 Webhook Errors", String.valueOf(totalWebhookErrors.get()), true));

        if (reason != null && !reason.isBlank()) {
            fields.add(new EmbedField("📋 Reason", reason, false));
        }

        // Bypass queue — send directly for shutdown
        sendEmbedDirect(new EmbedPayload(
                "🔴 NothingLink Plugin Shutting Down",
                desc.toString(),
                COLOR_SHUTDOWN,
                "NothingLink v" + version + " • Shutdown",
                fields,
                null));
    }

    /**
     * Log a critical/crash error before shutdown.
     */
    public void logCritical(String title, String description, Throwable error) {
        List<EmbedField> fields = new ArrayList<>();
        fields.add(new EmbedField("⏱️ Uptime", getUptime(), true));
        fields.add(new EmbedField("🎵 Tracks Loaded", String.valueOf(totalTracksLoaded.get()), true));
        fields.add(new EmbedField("❌ Track Errors", String.valueOf(totalTrackErrors.get()), true));

        if (error != null) {
            fields.add(new EmbedField("💣 Exception", "```\n" + truncate(error.toString(), 900) + "\n```", false));
            String stackTrace = getStackTrace(error);
            if (!stackTrace.isEmpty()) {
                fields.add(new EmbedField("📜 Stack Trace", "```java\n" + truncate(stackTrace, 900) + "\n```", false));
            }
        }

        // Bypass queue — send directly for critical errors
        sendEmbedDirect(new EmbedPayload(
                "🚨 " + (title != null ? title : "CRITICAL ERROR"),
                description,
                COLOR_CRITICAL,
                "NothingLink • Critical",
                fields,
                null));
    }

    // ════════════════════════════════════════════════════════════════
    // LEGACY API — Backward compatibility
    // ════════════════════════════════════════════════════════════════

    /** Send an info embed (green). */
    public void info(String title, String description) {
        enqueue(new EmbedPayload(title, description, COLOR_SUCCESS, null, null, null));
    }

    /** Send a warning embed (yellow). */
    public void warn(String title, String description) {
        enqueue(new EmbedPayload(title, description, COLOR_WARNING, null, null, null));
    }

    /** Send an error embed (red). */
    public void error(String title, String description) {
        enqueue(new EmbedPayload(title, description, COLOR_ERROR, null, null, null));
    }

    /** Send a startup summary embed (purple). */
    public void startup(String title, String description, String footer) {
        enqueue(new EmbedPayload(title, description, COLOR_STARTUP, footer, null, null));
    }

    // ════════════════════════════════════════════════════════════════
    // QUEUE PROCESSING
    // ════════════════════════════════════════════════════════════════

    private void enqueue(EmbedPayload payload) {
        if (shuttingDown) {
            // During shutdown, send directly
            sendEmbedDirect(payload);
            return;
        }
        if (!sendQueue.offer(payload)) {
            log.debug("Webhook queue full, dropping message: {}", payload.title);
        }
    }

    private void processQueue() {
        while (!shuttingDown || !sendQueue.isEmpty()) {
            try {
                EmbedPayload payload = sendQueue.poll(1, TimeUnit.SECONDS);
                if (payload == null)
                    continue;

                // Rate limiting
                long now = System.currentTimeMillis();
                long elapsed = now - lastSendTime;
                if (elapsed < MIN_SEND_INTERVAL_MS) {
                    Thread.sleep(MIN_SEND_INTERVAL_MS - elapsed);
                }

                sendEmbedDirect(payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.debug("Queue processing error: {}", e.getMessage());
            }
        }
    }

    private void sendEmbedDirect(EmbedPayload payload) {
        try {
            ObjectNode root = JSON.createObjectNode();
            root.put("username", botName);
            if (avatarUrl != null && !avatarUrl.isBlank()) {
                root.put("avatar_url", avatarUrl);
            }
            root.putNull("content");

            ArrayNode embeds = root.putArray("embeds");
            ObjectNode embed = embeds.addObject();

            // Title
            if (payload.title != null)
                embed.put("title", truncate(payload.title, 256));

            // Description
            if (payload.description != null && !payload.description.isEmpty()) {
                embed.put("description", truncate(payload.description, 4096));
            }

            // Color
            embed.put("color", payload.color);

            // Timestamp
            embed.put("timestamp", Instant.now().toString());

            // Fields
            if (payload.fields != null && !payload.fields.isEmpty()) {
                ArrayNode fieldsArray = embed.putArray("fields");
                for (EmbedField field : payload.fields) {
                    ObjectNode fieldNode = fieldsArray.addObject();
                    fieldNode.put("name", truncate(field.name, 256));
                    fieldNode.put("value", truncate(field.value, 1024));
                    fieldNode.put("inline", field.inline);
                }
            }

            // Footer
            ObjectNode footer = embed.putObject("footer");
            StringBuilder footerText = new StringBuilder();
            if (payload.footer != null && !payload.footer.isEmpty()) {
                footerText.append(payload.footer);
            } else {
                footerText.append("NothingLink Logger");
            }
            footerText.append(" • Uptime: ").append(getUptime());
            footer.put("text", footerText.toString());

            // Thumbnail (optional)
            if (payload.thumbnailUrl != null && !payload.thumbnailUrl.isBlank()) {
                ObjectNode thumbnail = embed.putObject("thumbnail");
                thumbnail.put("url", payload.thumbnailUrl);
            }

            String body = JSON.writeValueAsString(root);
            HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            lastSendTime = System.currentTimeMillis();
            totalWebhooksSent.incrementAndGet();

            if (resp.statusCode() == 429) {
                // Rate limited — parse retry-after and wait
                log.warn("Webhook rate limited (429). Waiting before retry...");
                try {
                    var retryJson = JSON.readTree(resp.body());
                    long retryAfterMs = (long) (retryJson.path("retry_after").asDouble(2.0) * 1000);
                    Thread.sleep(retryAfterMs + 100);
                } catch (Exception e) {
                    Thread.sleep(5000);
                }
                // Retry once
                resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
                lastSendTime = System.currentTimeMillis();
            }

            if (resp.statusCode() >= 400) {
                totalWebhookErrors.incrementAndGet();
                log.debug("Webhook returned HTTP {} — body: {}", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            totalWebhookErrors.incrementAndGet();
            log.debug("Webhook send failed: {}", e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // SHUTDOWN
    // ════════════════════════════════════════════════════════════════

    /**
     * Flush all pending webhook messages and shut down.
     * Blocks up to 15 seconds for the queue to drain.
     */
    public void shutdown() {
        shuttingDown = true;
        log.info("Webhook logger shutting down — flushing {} pending messages...", sendQueue.size());

        // Drain remaining messages
        List<EmbedPayload> remaining = new ArrayList<>();
        sendQueue.drainTo(remaining);
        for (EmbedPayload payload : remaining) {
            sendEmbedDirect(payload);
        }

        scheduler.shutdown();
        sender.shutdown();
        try {
            sender.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Webhook logger shut down. Total sent: {}, errors: {}",
                totalWebhooksSent.get(), totalWebhookErrors.get());
    }

    // ════════════════════════════════════════════════════════════════
    // STATISTICS GETTERS
    // ════════════════════════════════════════════════════════════════

    public long getTotalTracksLoaded() {
        return totalTracksLoaded.get();
    }

    public long getTotalTrackErrors() {
        return totalTrackErrors.get();
    }

    public long getTotalSearches() {
        return totalSearches.get();
    }

    public long getTotalWebhooksSent() {
        return totalWebhooksSent.get();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public String getUptime() {
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHours() % 24;
        long mins = uptime.toMinutes() % 60;
        long secs = uptime.getSeconds() % 60;

        if (days > 0)
            return String.format("%dd %dh %dm %ds", days, hours, mins, secs);
        if (hours > 0)
            return String.format("%dh %dm %ds", hours, mins, secs);
        if (mins > 0)
            return String.format("%dm %ds", mins, secs);
        return String.format("%ds", secs);
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private static String truncate(String s, int maxLen) {
        if (s == null)
            return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private static String formatDuration(long ms) {
        if (ms <= 0)
            return "Live / Unknown";
        long secs = ms / 1000;
        long mins = secs / 60;
        long hours = mins / 60;
        secs %= 60;
        mins %= 60;
        if (hours > 0)
            return String.format("%d:%02d:%02d", hours, mins, secs);
        return String.format("%d:%02d", mins, secs);
    }

    private static String getStackTrace(Throwable t) {
        if (t == null)
            return "";
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String full = sw.toString();
        // Trim to a reasonable length
        String[] lines = full.split("\n");
        StringBuilder sb = new StringBuilder();
        int maxLines = Math.min(lines.length, 15);
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append("\n");
        }
        if (lines.length > maxLines) {
            sb.append("... ").append(lines.length - maxLines).append(" more lines\n");
        }
        return sb.toString();
    }

    // ════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ════════════════════════════════════════════════════════════════

    private record EmbedPayload(
            String title,
            String description,
            int color,
            String footer,
            List<EmbedField> fields,
            String thumbnailUrl) {
    }

    private record EmbedField(
            String name,
            String value,
            boolean inline) {
    }
}