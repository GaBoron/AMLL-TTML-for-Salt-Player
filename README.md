# AMLL TTML Loader for Salt Player

AMLL TTML Loader is a Salt Player for Windows plugin that searches AMLL TTML DB,
loads TTML lyrics, converts them into Salt Player compatible timed lyrics, and
falls back to local lyrics when no reliable online match is available.

This project was built with assistance from Codex.

## Features

- Searches `amll-dev/amll-ttml-db` using the current track title, artist, and
  album metadata.
- Converts TTML word-level lyrics into SPL-style timed lyrics for Salt Player.
- Handles main lyrics, duet/agent lines, translations, romanization, and
  background vocals.
- Uses title-first automatic matching to reduce incorrect lyric matches.
- Waits up to 10 seconds for an online AMLL search, then falls back to local or
  default Salt Player lyrics if the search is still pending.
- Permanently caches successful AMLL matches so the same song can load
  immediately next time.
- Records failed automatic matches for seven days, then retries after the miss
  cache expires.
- Shows a compact source line for plugin-loaded lyrics: `来源：AMLL` or
  `来源：本地`.
- Provides a manual matching dialog from the plugin settings page, including
  editable title/artist/album fields, AMLL search results, lyric previews, and a
  local/default override option.

## Installation

1. Download `AMLL-TTML-Loader-1.0.0.zip` from the latest GitHub Release.
2. Copy the zip file to:

   ```text
   %APPDATA%\Salt Player for Windows\workshop\plugins
   ```

3. Restart Salt Player for Windows.
4. Optional: open the plugin settings and use `手动匹配当前歌曲` to manually choose
   an AMLL result or force the current song to use local/default lyrics.

## Build from Source

Requirements:

- JDK 21
- Network access for the first Gradle wrapper run, unless Gradle is already
  cached locally

Build the plugin package:

```powershell
.\gradlew.bat --no-daemon plugin
```

The plugin package is written to:

```text
out\plugin\AMLL-TTML-Loader-1.0.0.zip
```

## Cache and Overrides

The plugin stores cache and manual overrides under:

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache
```

Important files:

- `raw-lyrics-index.jsonl`: cached AMLL index
- `song-cache.tsv`: successful AMLL lyric matches
- `manual-overrides.tsv`: manual AMLL or local/default choices
- `miss-cache.tsv`: seven-day automatic-match miss records
- `lyrics\*.spl`: converted lyric cache

## Limitations

- Salt Player's current plugin API only lets plugins provide lyrics before Salt
  Player loads them. The plugin cannot reliably show local lyrics first and then
  replace them with online lyrics during the same playback.
- Embedded metadata lyrics loaded internally by Salt Player cannot be tagged with
  a source line by this plugin.
- Online matching depends on AMLL TTML DB coverage, current network access, and
  the quality of the track metadata.

## Credits

- [amll-dev/amll-ttml-db](https://github.com/amll-dev/amll-ttml-db) for the TTML
  lyric database.
- [Moriafly/spw-workshop-api](https://github.com/Moriafly/spw-workshop-api) for
  the Salt Player workshop API reference.
- Salt Player for Windows for the plugin platform.
- PF4J and Gradle for the plugin and build ecosystem.
