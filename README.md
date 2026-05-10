# 🎵 NothingLink

A multi-source audio plugin for [Lavalink](https://github.com/lavalink-devs/Lavalink) — supports **Amazon Music**, **Instagram**, and **Pandora**.

Designed to work seamlessly alongside existing plugins like [LavaSrc](https://github.com/topi314/LavaSrc) with **zero conflicts**.

---

## ✨ Features

### 🎧 Amazon Music
- 🔍 Search tracks via `amzsearch:` prefix
- 🎶 Play individual tracks, albums, artists & playlists via URL
- 📋 Community playlists & user playlists supported
- 🔗 Full LavaSearch integration

### 📸 Instagram
- 🎬 Play audio from Reels, Posts & Audio pages
- 🔄 Auto session management (no login required)
- ⏳ CDN URL auto-refresh on expiry

### 🎵 Pandora
- 🔍 Search tracks via `pdsearch:` prefix
- 🎶 Play tracks, albums, artists & playlists via URL
- 👤 Artist "All Songs" supported
- 💡 Recommendations via `pdrec:` prefix
- 🔁 ISRC-based mirroring (plays via YouTube fallback)

---

## 📦 Installation

Add to your Lavalink `application.yml`:

```yaml
lavalink:
    plugins:
        - dependency: com.github.Ankush26030:NothingLink:v1.0.5
          repository: https://jitpack.io
```

---

## ⚙️ Configuration

Add the following under `plugins:` in your `application.yml`:

### Amazon Music

```yaml
plugins:
    amazonmusic:
        enabled: true
        searchLimit: 10
        providers:
            - "ytsearch:\"%ISRC%\""
            - "ytsearch:%QUERY%"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable/disable Amazon Music source |
| `searchLimit` | integer | `10` | Max search results (1-10) |
| `providers` | string[] | YouTube search | Audio resolution providers |

### Instagram

```yaml
plugins:
    instagram:
        enabled: true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable/disable Instagram source |

### Pandora

```yaml
plugins:
    pandora:
        enabled: true
        tokenApiUrl: "https://get.1lucas1apk.fun/pandora/gettoken"
        csrfToken: ""
        preferTokenApi: true
        searchLimit: 6
        providers:
            - "ytsearch:\"%ISRC%\""
            - "ytsearch:%QUERY%"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable/disable Pandora source |
| `tokenApiUrl` | string | `""` | External token API endpoint |
| `csrfToken` | string | `""` | Manual CSRF token (optional) |
| `preferTokenApi` | boolean | `true` | Try external token API first |
| `searchLimit` | integer | `6` | Max search results |
| `providers` | string[] | YouTube search | Audio resolution providers |

### 📡 Discord Webhook Logger
- 🚀 **Detailed Startup**: Logs plugin version, system info, and source status.
- ▶️ **Track Events**: Logs when tracks start, end (with reason), or fail.
- 🔍 **Activity Logs**: See what users are searching for in real-time.
- 🛑 **Shutdown Reports**: Final session summary (uptime, total tracks) on shutdown or crash.
- 🛡️ **Rate Limited**: Built-in protection to stay safe from Discord webhook bans.

```yaml
plugins:
    nothinglink:
        webhookEnabled: true
        webhookUrl: "YOUR_DISCORD_WEBHOOK_URL"
        webhookBotName: "NothingLink"
        webhookAvatarUrl: ""
        logTrackEvents: true
        logSearchEvents: true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `webhookEnabled` | boolean | `false` | Enable/disable Discord webhook logs |
| `webhookUrl` | string | `""` | Discord webhook URL |
| `webhookBotName` | string | `"NothingLink"` | Name of the bot in Discord |
| `webhookAvatarUrl`| string | `""` | URL for the bot's avatar |
| `logTrackEvents` | boolean | `true` | Log song start/stop/error |
| `logSearchEvents` | boolean | `false` | Log user searches |

### Full Example

```yaml
plugins:
    amazonmusic:
        enabled: true
        searchLimit: 10
        providers:
            - "ytsearch:\"%ISRC%\""
            - "ytsearch:%QUERY%"
    instagram:
        enabled: true
    pandora:
        enabled: true
        tokenApiUrl: "https://get.1lucas1apk.fun/pandora/gettoken"
        csrfToken: ""
        preferTokenApi: true
        searchLimit: 6
        providers:
            - "ytsearch:\"%ISRC%\""
            - "ytsearch:%QUERY%"
    nothinglink:
        webhookEnabled: true
        webhookUrl: "YOUR_DISCORD_WEBHOOK_URL"  
        webhookBotName: "NothingLink Logger"
        logTrackEvents: true
```

---

## 🔗 Supported URLs & Prefixes

### Amazon Music
```
https://music.amazon.com/tracks/B0XXXXX
https://music.amazon.com/albums/B0XXXXX
https://music.amazon.com/artists/B0XXXXX
https://music.amazon.com/playlists/B0XXXXX
https://music.amazon.com/community-playlists/B0XXXXX
https://music.amazon.com/user-playlists/B0XXXXX
```
Search: `amzsearch:Shape of You`

### Instagram
```
https://www.instagram.com/reel/XXXXXXXXX/
https://www.instagram.com/p/XXXXXXXXX/
https://www.instagram.com/reels/audio/XXXXXXXXX/
```

### Pandora
```
https://www.pandora.com/artist/artist-name/song-name/TRXXXXX
https://www.pandora.com/artist/artist-name/album-name/ALXXXXX
https://www.pandora.com/artist/artist-name/ARXXXXX
https://www.pandora.com/playlist/PL:XXXXX
```
Search: `pdsearch:Shape of You`
Recommendations: `pdrec:TRXXXXX`

---

## 🤝 Compatibility

Works with all standard Lavalink plugins:

| Plugin | Status |
|--------|--------|
| LavaSrc | ✅ |
| LavaSearch | ✅ |
| YouTube Source | ✅ |
| SponsorBlock | ✅ |
| LavaDSPX | ✅ |
| Dunctebot | ✅ |
| Gaana Plugin | ✅ |

---

## 🛠️ Build from Source

Requires **Java 21+**

```bash
git clone https://github.com/Ankush26030/NothingLink.git
cd NothingLink
./gradlew clean build
```

Output: `build/libs/NothingLink-1.0.5.jar`

---

## 📄 License

**All Rights Reserved** — See [LICENSE](LICENSE) for details.

You may **use** this plugin with Lavalink. You may **not** copy, modify, or redistribute the source code.