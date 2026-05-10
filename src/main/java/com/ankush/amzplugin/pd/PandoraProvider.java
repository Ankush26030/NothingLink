package com.ankush.amzplugin.pd;

import com.fasterxml.jackson.databind.JsonNode;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.*;
import com.ankush.amzplugin.ExtendedAudioPlaylist;
import com.ankush.amzplugin.mirror.DefaultMirroringAudioTrackResolver;
import com.ankush.amzplugin.mirror.MirroringAudioSourceManager;

import java.io.DataInput;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lavalink AudioSourceManager for Pandora — search, tracks, albums, artists,
 * playlists, recommendations.
 */
public class PandoraProvider extends MirroringAudioSourceManager {

    private static final String SRC = "pandora";
    private static final String PFX_SEARCH = "pdsearch:";
    private static final String PFX_REC = "pdrec:";
    private static final String HOME = "https://www.pandora.com";
    private static final Pattern URL_RE = Pattern.compile(
            "^@?(?:https?://)?(?:www\\.)?pandora\\.com/(?:playlist/(?<pl>PL:[\\d:]+)|artist/[\\w\\-]+(?:/[\\w\\-]+)*/(?<eid>(?:TR|AL|AR)[A-Za-z0-9]+))(?:[?#].*)?$");

    private final PandoraClient client;
    private final int maxResults;
    private com.ankush.amzplugin.plugin.DiscordWebhookLogger webhook;

    public PandoraProvider(String[] providers, String tokenUrl, String csrf, boolean extFirst, int limit,
            Function<Void, AudioPlayerManager> apm) {
        super(apm, new DefaultMirroringAudioTrackResolver(providers));
        this.maxResults = Math.max(limit, 1);
        this.client = new PandoraClient(this::getHttpInterface, tokenUrl, csrf, extFirst);
    }

    @Override
    public String getSourceName() {
        return SRC;
    }

    public void setWebhook(com.ankush.amzplugin.plugin.DiscordWebhookLogger webhook) {
        this.webhook = webhook;
    }

    @Override
    public AudioPlayerManager getAudioPlayerManager() {
        return audioPlayerManager.apply(null);
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager mgr, AudioReference ref) {
        String id = ref.identifier;
        try {
            if (id.startsWith(PFX_SEARCH)) {
                String q = id.substring(PFX_SEARCH.length()).trim();
                return q.isEmpty() ? AudioReference.NO_TRACK : doSearch(q);
            }
            if (id.startsWith(PFX_REC)) {
                String t = id.substring(PFX_REC.length()).trim();
                return t.isEmpty() ? AudioReference.NO_TRACK : doRecommendations(t);
            }
            Matcher m = URL_RE.matcher(id.trim());
            if (!m.find())
                return null;
            String eid = m.group("pl") != null ? m.group("pl") : m.group("eid");
            if (eid == null)
                return null;
            if (eid.startsWith("TR"))
                return doTrack(eid);
            if (eid.startsWith("AL"))
                return doAlbum(eid);
            if (eid.startsWith("AR"))
                return id.contains("/artist/all-songs/") ? doArtistAll(eid) : doArtist(eid);
            if (eid.startsWith("PL:"))
                return doPlaylist(eid);
            return null;
        } catch (IOException e) {
            if (webhook != null)
                webhook.logLoadFailed(id, getSourceName(), e.getMessage());
            throw new FriendlyException("Pandora load error", FriendlyException.Severity.SUSPICIOUS, e);
        }
    }

    // ── loaders ─────────────────────────────────────────────────────────────

    private AudioItem doSearch(String query) throws IOException {
        JsonNode j = client.search(query);
        if (j == null)
            return AudioReference.NO_TRACK;
        JsonNode ann = j.get("annotations"), res = j.get("results");
        if (res == null || !res.isArray() || res.isEmpty())
            return AudioReference.NO_TRACK;
        List<AudioTrack> out = new ArrayList<>();
        for (JsonNode n : res) {
            if (out.size() >= maxResults)
                break;
            String rid = n.asText(null);
            if (rid == null)
                continue;
            JsonNode item = ann.get(rid);
            if (item == null || !"TR".equals(txt(item, "type")))
                continue;
            AudioTrack t = buildTrack(item, ann);
            if (t != null)
                out.add(t);
        }
        if (webhook != null)
            webhook.logSearch(query, getSourceName(), out.size());
        return out.isEmpty() ? AudioReference.NO_TRACK
                : new PandoraCollection("Pandora Search: " + query, out, ExtendedAudioPlaylist.Type.PLAYLIST, null,
                        null, null, out.size());
    }

    private AudioItem doTrack(String tid) throws IOException {
        JsonNode d = client.details(tid);
        if (d == null)
            return AudioReference.NO_TRACK;
        JsonNode ann = d.get("annotations"), node = findById(tid, ann);
        if (node == null)
            return AudioReference.NO_TRACK;
        AudioTrack t = buildTrack(node, ann);
        return t != null ? t : AudioReference.NO_TRACK;
    }

    private AudioItem doAlbum(String aid) throws IOException {
        JsonNode d = client.details(aid);
        if (d == null)
            return AudioReference.NO_TRACK;
        JsonNode ann = d.get("annotations"), album = findById(aid, ann);
        if (album == null)
            return AudioReference.NO_TRACK;
        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode tArr = album.get("tracks");
        if (tArr != null && tArr.isArray())
            for (JsonNode v : tArr) {
                String id = v.asText(null);
                if (id == null)
                    continue;
                JsonNode n = ann.get(id);
                if (n != null) {
                    AudioTrack t = buildTrack(n, ann);
                    if (t != null)
                        tracks.add(t);
                }
            }
        String url = txt(album, "shareableUrlPath");
        int total = album.has("trackCount") ? album.get("trackCount").asInt(tracks.size()) : tracks.size();
        return new PandoraCollection(txt(album, "name"), tracks, ExtendedAudioPlaylist.Type.ALBUM,
                url != null ? HOME + url : null, PandoraClient.artworkFor(album), txt(album, "artistName"), total);
    }

    private AudioItem doArtist(String artId) throws IOException {
        JsonNode d = client.details(artId);
        if (d == null)
            return AudioReference.NO_TRACK;
        JsonNode ann = d.get("annotations"), artist = findById(artId, ann);
        if (artist == null)
            return AudioReference.NO_TRACK;
        List<AudioTrack> tracks = new ArrayList<>();
        JsonNode top = d.at("/artistDetails/topTracks");
        if (top != null && top.isArray())
            for (JsonNode v : top) {
                String id = v.asText(null);
                if (id == null)
                    continue;
                JsonNode n = ann.get(id);
                if (n != null) {
                    AudioTrack t = buildTrack(n, ann);
                    if (t != null)
                        tracks.add(t);
                }
            }
        String name = txt(artist, "name"), url = txt(artist, "shareableUrlPath");
        return new PandoraCollection((name != null ? name : "Artist") + "'s Top Tracks", tracks,
                ExtendedAudioPlaylist.Type.ARTIST, url != null ? HOME + url : null, PandoraClient.artworkFor(artist),
                name, tracks.size());
    }

    private AudioItem doArtistAll(String artId) throws IOException {
        JsonNode j = client.artistAllTracks(artId);
        if (j == null)
            return AudioReference.NO_TRACK;
        JsonNode ann = j.get("annotations"), tNode = j.get("tracks");
        if (tNode == null || !tNode.isArray() || tNode.isEmpty())
            return AudioReference.NO_TRACK;
        Map<String, JsonNode> pool = indexAnnotations(ann);
        List<String> ids = new ArrayList<>();
        for (JsonNode v : tNode) {
            String s = v.asText(null);
            if (s != null)
                ids.add(s);
        }
        List<String> missing = new ArrayList<>();
        for (String id : ids)
            if (!pool.containsKey(id))
                missing.add(id);
        if (!missing.isEmpty()) {
            JsonNode extra = client.annotate(missing);
            for (String id : missing) {
                JsonNode n = extra.get(id);
                if (n != null)
                    pool.put(id, n);
            }
        }
        List<AudioTrack> tracks = new ArrayList<>();
        for (String id : ids) {
            JsonNode n = pool.get(id);
            if (n != null) {
                AudioTrack t = buildTrack(n, ann);
                if (t != null)
                    tracks.add(t);
            }
        }
        JsonNode artist = findById(artId, ann);
        if (artist == null) {
            JsonNode d = client.details(artId);
            if (d != null)
                artist = findById(artId, d.get("annotations"));
        }
        String name = artist != null ? txt(artist, "name") : null;
        String path = artist != null ? txt(artist, "shareableUrlPath") : null;
        return new PandoraCollection(name != null ? name + " - All Songs" : "All Songs", tracks,
                ExtendedAudioPlaylist.Type.ARTIST, path != null ? HOME + path : null,
                artist != null ? PandoraClient.artworkFor(artist) : null, name, tracks.size());
    }

    private AudioItem doPlaylist(String plId) throws IOException {
        JsonNode j = client.playlistTracks(plId);
        if (j == null)
            return AudioReference.NO_TRACK;
        JsonNode ann = j.get("annotations"), tNode = j.get("tracks");
        Map<String, JsonNode> pool = indexAnnotations(ann);
        List<String> ids = new ArrayList<>();
        if (tNode != null && tNode.isArray())
            for (JsonNode v : tNode) {
                String s = txt(v, "pandoraId");
                if (s != null)
                    ids.add(s);
            }
        List<String> missing = new ArrayList<>();
        for (String id : ids)
            if (!pool.containsKey(id))
                missing.add(id);
        if (!missing.isEmpty()) {
            JsonNode extra = client.annotate(missing);
            for (String id : missing) {
                JsonNode n = extra.get(id);
                if (n != null)
                    pool.put(id, n);
            }
        }
        List<AudioTrack> tracks = new ArrayList<>();
        for (String id : ids) {
            JsonNode n = pool.get(id);
            if (n != null) {
                AudioTrack t = buildTrack(n, ann);
                if (t != null)
                    tracks.add(t);
            }
        }
        String name = txt(j, "name"), path = txt(j, "shareableUrlPath");
        String author = null;
        String lid = txt(j, "listenerPandoraId");
        if (lid != null && ann != null) {
            JsonNode a = ann.get(lid);
            if (a != null)
                author = txt(a, "fullname");
        }
        return new PandoraCollection(name != null ? name : "Pandora Playlist", tracks,
                ExtendedAudioPlaylist.Type.PLAYLIST,
                path != null ? HOME + path : null, PandoraClient.artworkFor(j), author, tracks.size());
    }

    private AudioItem doRecommendations(String tid) throws IOException {
        JsonNode d = client.details(tid);
        if (d == null)
            return AudioReference.NO_TRACK;
        JsonNode sim = d.at("/trackDetails/similarTracks");
        if (sim == null || !sim.isArray() || sim.isEmpty())
            return AudioReference.NO_TRACK;
        List<String> ids = new ArrayList<>();
        for (JsonNode v : sim) {
            String s = v.asText(null);
            if (s != null)
                ids.add(s);
        }
        JsonNode ann = client.annotate(ids);
        List<AudioTrack> tracks = new ArrayList<>();
        for (String id : ids) {
            JsonNode n = ann.get(id);
            if (n != null) {
                AudioTrack t = buildTrack(n, ann);
                if (t != null)
                    tracks.add(t);
            }
        }
        if (webhook != null)
            webhook.logSearch("Recommendations for: " + tid, getSourceName(), tracks.size());
        return new PandoraCollection("Pandora Recommendations", tracks, ExtendedAudioPlaylist.Type.RECOMMENDATIONS,
                null, null, null, tracks.size());
    }

    // ── track builder ───────────────────────────────────────────────────────

    private AudioTrack buildTrack(JsonNode n, JsonNode ann) {
        String title = txt(n, "name");
        if (title == null)
            return null;
        String artist = txt(n, "artistName");
        if (artist == null)
            artist = "Unknown";
        long dur = n.path("duration").asLong(0) * 1000;
        if (dur == 0)
            return null;
        String pid = txt(n, "pandoraId"), urlPath = txt(n, "shareableUrlPath"), isrc = txt(n, "isrc");
        String albName = null, albUrl = null;
        String albId = txt(n, "albumId");
        if (albId != null && ann != null) {
            JsonNode a = ann.get(albId);
            if (a != null) {
                albName = txt(a, "name");
                String p = txt(a, "shareableUrlPath");
                albUrl = p != null ? HOME + p : null;
            }
        }
        String artUrl = null, artArt = null;
        String artId = txt(n, "artistId");
        if (artId != null && ann != null) {
            JsonNode a = ann.get(artId);
            if (a != null) {
                String p = txt(a, "shareableUrlPath");
                artUrl = p != null ? HOME + p : null;
                artArt = PandoraClient.artworkFor(a);
            }
        }
        var info = new AudioTrackInfo(title, artist, dur, pid, false, urlPath != null ? HOME + urlPath : null,
                PandoraClient.artworkFor(n), isrc);
        return new PandoraTrack(info, albName, albUrl, artUrl, artArt, null, false, this);
    }

    // ── util ────────────────────────────────────────────────────────────────

    private static JsonNode findById(String suffix, JsonNode ann) {
        if (ann == null || ann.isNull())
            return null;
        var it = ann.fields();
        while (it.hasNext()) {
            var e = it.next();
            JsonNode v = e.getValue();
            String p = txt(v, "shareableUrlPath");
            if (p != null && p.endsWith("/" + suffix))
                return v;
            String s = txt(v, "slugPlusPandoraId");
            if (s != null && s.contains(suffix))
                return v;
        }
        return null;
    }

    private static Map<String, JsonNode> indexAnnotations(JsonNode ann) {
        Map<String, JsonNode> map = new HashMap<>();
        if (ann != null && !ann.isNull()) {
            var it = ann.fields();
            while (it.hasNext()) {
                var e = it.next();
                String pid = txt(e.getValue(), "pandoraId");
                if (pid != null)
                    map.put(pid, e.getValue());
            }
        }
        return map;
    }

    private static String txt(JsonNode n, String f) {
        if (n == null || n.isNull() || !n.has(f) || n.get(f).isNull())
            return null;
        String v = n.get(f).asText(null);
        return (v != null && !v.isEmpty()) ? v : null;
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo info, DataInput input) throws IOException {
        var ext = super.decodeTrack(input);
        return new PandoraTrack(info, ext.albumName, ext.albumUrl, ext.artistUrl, ext.artistArtworkUrl, ext.previewUrl,
                ext.isPreview, this);
    }
}