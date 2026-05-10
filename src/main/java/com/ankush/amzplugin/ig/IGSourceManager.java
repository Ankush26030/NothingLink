package com.ankush.amzplugin.ig;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lavalink AudioSourceManager for Instagram posts, reels, and audio pages.
 */
public class IGSourceManager implements AudioSourceManager {

    private static final Logger log = LoggerFactory.getLogger(IGSourceManager.class);

    private static final Pattern RE_AUDIO = Pattern.compile("https?://(?:www\\.)?instagram\\.com/reels/audio/(\\d+)");
    private static final Pattern RE_POST = Pattern.compile("https?://(?:www\\.)?instagram\\.com/p/([\\w-]+)");
    private static final Pattern RE_REEL = Pattern
            .compile("https?://(?:www\\.)?instagram\\.com/(?:reels?|reel)/([\\w-]+)");

    private final IGMediaResolver resolver;
    private final HttpInterfaceManager httpManager;
    private com.ankush.amzplugin.plugin.DiscordWebhookLogger webhook;

    public IGSourceManager() {
        this.resolver = new IGMediaResolver();
        this.httpManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    @Override
    public String getSourceName() {
        return "instagram";
    }

    public IGMediaResolver getResolver() {
        return resolver;
    }

    public void setWebhook(com.ankush.amzplugin.plugin.DiscordWebhookLogger webhook) {
        this.webhook = webhook;
    }

    public HttpInterface getHttpInterface() {
        return httpManager.getInterface();
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference ref) {
        String id = ref.identifier;
        try {
            Matcher m;
            if ((m = RE_AUDIO.matcher(id)).find())
                return fromAudio(id, m.group(1));
            if ((m = RE_POST.matcher(id)).find())
                return fromPost(id, m.group(1), "p");
            if ((m = RE_REEL.matcher(id)).find())
                return fromPost(id, m.group(1), "reel");
        } catch (Exception e) {
            if (webhook != null)
                webhook.logLoadFailed(id, getSourceName(), e.getMessage());
            throw new FriendlyException("Instagram load failed", FriendlyException.Severity.SUSPICIOUS, e);
        }
        return null;
    }

    private AudioItem fromAudio(String url, String audioId) {
        IGMediaResolver.MediaInfo info = resolver.resolveAudioClip(audioId);
        return info != null ? toTrack(info, url, audioId) : AudioReference.NO_TRACK;
    }

    private AudioItem fromPost(String url, String code, String segment) {
        IGMediaResolver.MediaInfo info = resolver.resolveByShortcode(code, segment);
        return info != null ? toTrack(info, url, code) : AudioReference.NO_TRACK;
    }

    private AudioItem toTrack(IGMediaResolver.MediaInfo mi, String url, String identifier) {
        if (mi.streamUrl == null)
            return AudioReference.NO_TRACK;
        AudioTrackInfo info = new AudioTrackInfo(mi.title, mi.author, mi.durationMs, identifier, false, url,
                mi.artworkUrl, null);
        return new IGTrack(info, mi.streamUrl, this);
    }

    @Override
    public boolean isTrackEncodable(AudioTrack t) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack t, DataOutput out) {
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo info, DataInput in) {
        return new IGTrack(info, null, this);
    }

    @Override
    public void shutdown() {
        try {
            httpManager.close();
        } catch (IOException e) {
            log.error("Failed closing IG http manager", e);
        }
    }
}