# 🎵 NothingLink

A standalone **Amazon Music** source plugin for [Lavalink](https://github.com/lavalink-devs/Lavalink).

Designed to work seamlessly alongside existing plugins like [LavaSrc](https://github.com/topi314/LavaSrc) with **zero conflicts**.

---

## ✨ Features

- 🔍 **Search** — Search Amazon Music tracks
- 🎶 **Tracks** — Play individual tracks via URL
- 💿 **Albums** — Load full albums
- 👤 **Artists** — Load artist top songs
- 📋 **Playlists** — Load playlists, community playlists & user playlists
- 🔗 **LavaSearch** — Full search integration support
- ⚡ **Conflict-Free** — Works alongside LavaSrc and all other Lavalink plugins

---

## 📦 Installation

Add to your Lavalink `application.yml`:

```yaml
lavalink:
    plugins:
        - dependency: com.github.Ankush26030:NothingLink:v1.0.0
          repository: https://jitpack.io
```

---

## ⚙️ Configuration

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

---

## 🔗 Supported URLs

```
https://music.amazon.com/tracks/B0XXXXX
https://music.amazon.com/albums/B0XXXXX
https://music.amazon.com/artists/B0XXXXX
https://music.amazon.com/playlists/B0XXXXX
https://music.amazon.com/community-playlists/B0XXXXX
https://music.amazon.com/user-playlists/B0XXXXX
```

### Search Prefix

```
amzsearch:Shape of You
```

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

---

## 🛠️ Build from Source

Requires **Java 21+**

```bash
git clone https://github.com/Ankush26030/NothingLink.git
cd NothingLink
./gradlew :plugin:build
```

Output: `plugin/build/libs/amazonmusic-plugin-1.0.0.jar`

---

## 📄 License

MIT
