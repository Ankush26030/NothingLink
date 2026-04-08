package com.ankush.amzplugin;

import com.ankush.amzplugin.mirror.DefaultMirroringAudioTrackResolver;
import com.ankush.amzplugin.mirror.MirroringAudioSourceManager;
import com.ankush.amzplugin.mirror.MirroringAudioTrackResolver;
import com.github.topi314.lavasearch.AudioSearchManager;
import com.github.topi314.lavasearch.result.AudioSearchResult;
import com.github.topi314.lavasearch.result.BasicAudioSearchResult;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpConfigurable;
import com.sedmelluq.discord.lavaplayer.track.*;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.DataInput;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AmazonMusicSourceManager extends MirroringAudioSourceManager implements HttpConfigurable, AudioSearchManager {
	private static final Logger log = LoggerFactory.getLogger(AmazonMusicSourceManager.class);
	public static final Pattern URL_PATTERN = Pattern.compile("https?://(?:www\\.)?music\\.amazon\\.[a-z.]+/(?<type>tracks|albums|artists|playlists|community-playlists|user-playlists)/(?<id>[A-Za-z0-9]+)(?:/[^?#]*)?(?:\\?.*)?", Pattern.CASE_INSENSITIVE);
	public static final String SEARCH_PREFIX = "amzsearch:";
	public static final Set<AudioSearchResult.Type> SEARCH_TYPES = Set.of(AudioSearchResult.Type.TRACK);
	private static final String API_BASE = "http://us2.leonodes.xyz:15482";
	private static final long FAILURE_CACHE_TTL_MS = 30_000L;
	private static final int FAILURE_CACHE_CLEANUP_THRESHOLD = 256;
	private int searchLimit = 10;
	private final Map<String, Long> recentFailures = new ConcurrentHashMap<>();

	public AmazonMusicSourceManager(String[] providers, AudioPlayerManager apm) { this(providers, unused -> apm); }
	public AmazonMusicSourceManager(String[] providers, Function<Void, AudioPlayerManager> apm) { this(apm, new DefaultMirroringAudioTrackResolver(providers)); }
	public AmazonMusicSourceManager(Function<Void, AudioPlayerManager> apm, MirroringAudioTrackResolver resolver) {
		super(apm, resolver);
		httpInterfaceManager.configureRequests(c -> RequestConfig.copy(c).setConnectTimeout(10000).setConnectionRequestTimeout(10000).setSocketTimeout(15000).build());
	}

	public void setSearchLimit(int sl) { this.searchLimit = sl; }
	@Override public String getSourceName() { return "amazonmusic"; }

	@Override public AudioTrack decodeTrack(AudioTrackInfo ti, DataInput input) throws IOException {
		var ext = super.decodeTrack(input);
		return new AmazonMusicAudioTrack(ti, ext.albumName, ext.albumUrl, ext.artistUrl, ext.artistArtworkUrl, ext.previewUrl, ext.isPreview, this);
	}

	@Override public AudioSearchResult loadSearch(String query, Set<AudioSearchResult.Type> types) {
		if (!query.startsWith(SEARCH_PREFIX)) return null;
		if (types.isEmpty()) types = SEARCH_TYPES;
		if (!types.contains(AudioSearchResult.Type.TRACK)) return null;
		try {
			var tracks = searchTracks(query.substring(SEARCH_PREFIX.length()).trim());
			return tracks.isEmpty() ? null : new BasicAudioSearchResult(tracks, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		} catch (Exception e) { log.error("Amazon Music search failed", e); return null; }
	}

	@Override public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
		String id = reference.identifier;
		if (id.startsWith(SEARCH_PREFIX)) {
			try { var tracks = searchTracks(id.substring(SEARCH_PREFIX.length()).trim()); return tracks.isEmpty() ? AudioReference.NO_TRACK : tracks.get(0); }
			catch (Exception e) { log.warn("Search failed for {}", id, e); return AudioReference.NO_TRACK; }
		}
		var m = URL_PATTERN.matcher(id);
		if (!m.matches()) return null;
		if (isRecentlyFailed(id)) return AudioReference.NO_TRACK;
		try {
			String type = m.group("type").toLowerCase();
			AudioItem res = switch (type) {
				case "tracks" -> resolveSong(id); case "albums" -> resolveAlbum(id); case "artists" -> resolveArtist(id);
				case "playlists" -> resolvePlaylist(id); case "community-playlists" -> resolveCommunityPlaylist(id);
				case "user-playlists" -> resolveUserPlaylist(id); default -> AudioReference.NO_TRACK;
			};
			if (res == null || res == AudioReference.NO_TRACK) { markFailed(id); return AudioReference.NO_TRACK; }
			return res;
		} catch (Exception e) { log.warn("Failed to load {}", id, e); markFailed(id); return AudioReference.NO_TRACK; }
	}

	private ArrayList<AudioTrack> searchTracks(String query) throws IOException {
		if (query == null || query.isBlank()) return new ArrayList<>();
		var request = new HttpGet(API_BASE + "/api/search/songs?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&page=1&limit=" + searchLimit);
		var json = AmzTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), request);
		var items = json != null ? json.get("data") : null;
		var tracks = new ArrayList<AudioTrack>();
		if (items == null || items.isNull() || items.values().isEmpty()) return tracks;
		for (var item : items.values()) { var t = parseTrack(item); if (t != null) tracks.add(t); }
		return tracks;
	}

	private AudioItem resolveSong(String url) throws IOException { var d = getDataJson("/api/songs?url=", url); if (d == null) return AudioReference.NO_TRACK; var t = parseTrack(d); return t != null ? t : AudioReference.NO_TRACK; }
	private AudioItem resolveAlbum(String url) throws IOException { return parseCollection(getDataJson("/api/albums?url=", url, true), "Amazon Music Album", ExtendedAudioPlaylist.Type.ALBUM, url, null); }
	private AudioItem resolveArtist(String url) throws IOException { var d = getDataJson("/api/artists?url=", url, true); String n = d != null ? d.get("name").safeText() : null; String dn = (n == null || n.isBlank()) ? "Artist Top Songs" : n + " - Top Songs"; return parseCollection(d, dn, ExtendedAudioPlaylist.Type.ARTIST, url, dn); }
	private AudioItem resolvePlaylist(String url) throws IOException { return parseCollection(getDataJson("/api/playlists?url=", url, true), "Amazon Music Playlist", ExtendedAudioPlaylist.Type.PLAYLIST, url, null); }
	private AudioItem resolveCommunityPlaylist(String url) throws IOException { return parseCollection(getDataJson("/api/community-playlists?url=", url, true), "Amazon Music Community Playlist", ExtendedAudioPlaylist.Type.PLAYLIST, url, null); }
	private AudioItem resolveUserPlaylist(String url) throws IOException { return parseCollection(getDataJson("/api/community-playlists?url=", url, true), "Amazon Music User Playlist", ExtendedAudioPlaylist.Type.PLAYLIST, url, null); }

	private boolean isRecentlyFailed(String id) { Long l = recentFailures.get(id); if (l == null) return false; if ((System.currentTimeMillis() - l) >= FAILURE_CACHE_TTL_MS) { recentFailures.remove(id, l); return false; } return true; }
	private void markFailed(String id) { long now = System.currentTimeMillis(); recentFailures.put(id, now); if (recentFailures.size() >= FAILURE_CACHE_CLEANUP_THRESHOLD) recentFailures.entrySet().removeIf(e -> (now - e.getValue()) >= FAILURE_CACHE_TTL_MS); }

	private JsonBrowser getDataJson(String pp, String url) throws IOException { return getDataJson(pp, url, false); }
	private JsonBrowser getDataJson(String pp, String url, boolean isrc) throws IOException {
		String fu = API_BASE + pp + URLEncoder.encode(url, StandardCharsets.UTF_8); if (isrc) fu += "&isrc=false";
		var json = AmzTools.fetchResponseAsJson(httpInterfaceManager.getInterface(), new HttpGet(fu)); return (json == null || json.isNull()) ? null : json.get("data");
	}

	private AudioItem parseCollection(JsonBrowser data, String fn, ExtendedAudioPlaylist.Type type, String fu, String on) {
		if (data == null || data.isNull()) return AudioReference.NO_TRACK;
		var tn = data.get("songs"); if (tn == null || tn.isNull() || tn.values().isEmpty()) tn = data.get("topSongs");
		if (tn == null || tn.isNull() || tn.values().isEmpty()) return AudioReference.NO_TRACK;
		var tracks = new ArrayList<AudioTrack>(); for (var i : tn.values()) { var t = parseTrack(i); if (t != null) tracks.add(t); }
		if (tracks.isEmpty()) return AudioReference.NO_TRACK;
		String name = on != null ? on : data.get("name").safeText(); if (name == null || name.isBlank()) name = fn;
		String url = data.get("url").safeText(); if (url == null || url.isBlank()) url = fu;
		String art = data.get("image").safeText(); String author = null;
		var an = data.get("artist"); if (an != null && !an.isNull()) author = an.get("name").safeText();
		Integer tt = null; long ts = data.get("totalSongs").asLong(0); if (ts > 0) tt = (int) ts;
		return new AmazonMusicAudioPlaylist(name, tracks, type, url, art, author, tt);
	}

	private AmazonMusicAudioTrack parseTrack(JsonBrowser item) {
		if (item == null || item.isNull()) return null;
		String id = item.get("id").text(); String title = item.get("title").text();
		if (title == null || title.isBlank()) title = item.get("name").text(); if (id == null || title == null) return null;
		String url = item.get("url").safeText(); if (url == null || url.isBlank()) url = "https://music.amazon.com/tracks/" + id;
		return new AmazonMusicAudioTrack(new AudioTrackInfo(title, parseArtistName(item), item.get("duration").asLong(0), id, false, url, item.get("image").safeText(), item.get("isrc").safeText()), null, null, parseArtistUrl(item), null, null, false, this);
	}

	private String parseArtistName(JsonBrowser item) {
		var a = item.get("artist"); if (a != null && !a.isNull()) { var n = a.get("name").safeText(); if (n != null && !n.isBlank()) return n; var r = a.safeText(); if (r != null && !r.isBlank()) return r; }
		var as = item.get("artists"); if (as != null && as.isList()) { var ns = as.values().stream().map(v -> v.get("name").safeText() != null ? v.get("name").safeText() : v.safeText()).filter(v -> v != null && !v.isBlank()).collect(Collectors.toList()); if (!ns.isEmpty()) return String.join(", ", ns); }
		String an = item.get("artistName").safeText(); return (an != null && !an.isBlank()) ? an : "Unknown";
	}

	private String parseArtistUrl(JsonBrowser item) { var a = item.get("artist"); if (a != null && !a.isNull()) { var u = a.get("url").safeText(); if (u != null && !u.isBlank()) return u; } return null; }

	@Override public void configureRequests(Function<RequestConfig, RequestConfig> c) { httpInterfaceManager.configureRequests(c); }
	@Override public void configureBuilder(Consumer<HttpClientBuilder> c) { httpInterfaceManager.configureBuilder(c); }
}
