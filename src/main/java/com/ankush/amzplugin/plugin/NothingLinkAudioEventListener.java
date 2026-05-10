package com.ankush.amzplugin.plugin;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Audio event listener that forwards all track events to the Discord webhook
 * logger.
 *
 * Hooks into:
 * - onTrackStart → logs when a track begins playing
 * - onTrackEnd → logs when a track finishes (with end reason)
 * - onTrackException → logs playback errors with stack traces
 * - onTrackStuck → logs when a track gets stuck (stream timeout)
 */
public class NothingLinkAudioEventListener extends AudioEventAdapter {

    private static final Logger log = LoggerFactory.getLogger(NothingLinkAudioEventListener.class);
    private final DiscordWebhookLogger webhook;

    public NothingLinkAudioEventListener(DiscordWebhookLogger webhook) {
        this.webhook = webhook;
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        if (webhook == null || track == null)
            return;
        try {
            AudioTrackInfo info = track.getInfo();
            String source = track.getSourceManager() != null
                    ? track.getSourceManager().getSourceName()
                    : "Unknown";

            webhook.logTrackStart(
                    info.title,
                    info.author,
                    source,
                    info.length,
                    info.uri);
        } catch (Exception e) {
            log.debug("Failed to log track start to webhook: {}", e.getMessage());
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (webhook == null || track == null)
            return;
        try {
            AudioTrackInfo info = track.getInfo();

            String reason = switch (endReason) {
                case FINISHED -> "✅ Finished normally";
                case LOAD_FAILED -> "❌ Load failed";
                case STOPPED -> "⏹️ Stopped by user";
                case REPLACED -> "🔄 Replaced by another track";
                case CLEANUP -> "🧹 Cleaned up (player destroyed)";
            };

            webhook.logTrackEnd(info.title, info.author, reason);
        } catch (Exception e) {
            log.debug("Failed to log track end to webhook: {}", e.getMessage());
        }
    }

    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
        if (webhook == null || track == null)
            return;
        try {
            AudioTrackInfo info = track.getInfo();
            String source = track.getSourceManager() != null
                    ? track.getSourceManager().getSourceName()
                    : "Unknown";

            webhook.logTrackError(info.title, info.author, source, exception);
        } catch (Exception e) {
            log.debug("Failed to log track exception to webhook: {}", e.getMessage());
        }
    }

    @Override
    public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
        if (webhook == null || track == null)
            return;
        try {
            AudioTrackInfo info = track.getInfo();
            webhook.logTrackStuck(info.title, info.author, thresholdMs);
        } catch (Exception e) {
            log.debug("Failed to log track stuck to webhook: {}", e.getMessage());
        }
    }
}