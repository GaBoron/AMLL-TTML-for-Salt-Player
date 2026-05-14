# AMLL TTML Loader for Salt Player

Salt Player for Windows plugin that searches AMLL TTML DB, converts TTML word
lyrics into Salt Player compatible timed lyrics, and falls back to local lyrics
when no reliable AMLL match is available.

This project was built with Codex.

## Features

- Loads `.ttml` lyrics from `amll-dev/amll-ttml-db`.
- Converts main lyrics, duet lines, translations, romanization, and background
  vocals into SPL-style timed lyric lines for Salt Player.
- Uses the current track metadata in this order: search AMLL first, then load
  local sidecar lyrics or let Salt Player continue with its own metadata/local
  lyric loader.
- Waits up to 10 seconds for an online AMLL search. If the online search does
  not finish in time, the plugin immediately falls back to local/default lyrics;
  the background search may still populate cache for the next playback.
- Shows a short first lyric line for plugin-loaded lyrics:
  - `来源：AMLL` for AMLL search results and AMLL cache.
  - `来源：本地` for plugin-loaded local sidecar `.ttml`, `.lrc`, or `.spl` files.
- Hides `[ti]`, `[ar]`, `[al]`, and `[by]` metadata tags so Salt Player does not
  show them as translated lyrics.
- Caches successful AMLL lyrics permanently.
- Records AMLL search misses for seven days, then tries again after the cache
  expires.
- Uses title-first matching: the song title must be similar enough before
  artist and album metadata can affect automatic matching.
- Provides a plugin settings button named `手动匹配当前歌曲`.
  - Edit title, artist, and album before searching.
  - Preview the first lyric lines from each AMLL result.
  - Save a chosen AMLL lyric as a permanent override for the current song.
  - Save a local/default override so the current song will stop searching AMLL.

## Build

Requirements:

- JDK 21
- Network access on the first Gradle wrapper run, unless Gradle is already
  cached locally

Build the plugin package:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon plugin
```

The plugin package is written to:

```text
out-cache-fix\plugin\AMLL-TTML-Loader-1.0.0.zip
```

Install it by copying the zip into:

```text
%APPDATA%\Salt Player for Windows\workshop\plugins
```

## Cache

The plugin stores cache and manual overrides under:

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache
```

Important files:

- `raw-lyrics-index.jsonl`: cached AMLL index
- `song-cache.tsv`: successful AMLL lyric matches
- `manual-overrides.tsv`: manual AMLL or local/default choices
- `miss-cache.tsv`: 24-hour AMLL miss records
- `lyrics\*.spl`: converted lyric cache

## Notes

Salt Player's current plugin API does not expose a callback for rewriting lyrics
after Salt Player has already loaded embedded metadata lyrics. If AMLL and
plugin sidecar loading both miss, the plugin returns `null` and Salt Player
continues with its default local or embedded metadata lyric behavior.
