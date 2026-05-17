# AMLL TTML Loader for Salt Player

[简体中文](README.md) | **English**

AMLL TTML Loader is a Salt Player for Windows plugin that searches AMLL TTML DB, loads TTML lyrics, converts word-level lyrics into Salt Player compatible SPL-style lyrics, and falls back to local or default lyrics when no reliable online match is available.

This project was built with assistance from Codex.

## Features

- Search AMLL TTML DB using the current track title, artist, and album metadata.
- Convert TTML word-level lyrics into SPL-style timed lyrics for Salt Player.
- Handle main lyrics, duet/agent lines, translations, romanization, and background vocals.
- Use title-first automatic matching to reduce incorrect lyric matches.
- Wait up to 10 seconds for online AMLL search, then fall back to local/default lyrics on failure or timeout.
- Support sidecar `.ttml`, `.lrc`, and `.spl` lyric files, plus TTML lyrics embedded in FLAC metadata.
- Recover complete lyric lines from truncated embedded local TTML metadata where possible.
- Cache successful AMLL matches so the same song can load quickly next time.
- Record failed automatic matches for seven days, then retry after the miss cache expires.
- Show a compact source line: `来源：AMLL` or `来源：本地`.
- Provide a manual matching dialog with editable title/artist/album fields, search result preview, and local/default lyric override.
- Provide a lyric offset setting to fix lyrics that are ahead of or behind playback.
- Hide embedded lyric metadata lines such as `[ti:xxx]` and `[ar:xxx]`.
- Add runtime logs for troubleshooting plugin loading, online search, TTML conversion, cache reads/writes, and manual matching.

## Screenshots

### Automatically Loaded AMLL Lyrics

![Automatically loaded AMLL lyrics](docs/images/lyrics-page.png)

### Manual Matching Dialog

![Manual matching dialog](docs/images/manual-match-dialog.png)

## Requirements

For users:

- Windows
- Salt Player for Windows
- Access to AMLL TTML DB / GitHub raw resources

For developers:

- JDK 21
- Network access for the first Gradle Wrapper run, unless Gradle is already cached locally

Regular users do not need JDK 21 to install the plugin. JDK 21 is only required when building from source.

## Installation

1. Download `AMLL-TTML-Loader-1.0.3.zip` from the latest GitHub Release.
2. Copy the zip file to:

   ```text
   %APPDATA%\Salt Player for Windows\workshop\plugins
   ```

3. Restart Salt Player for Windows.
4. Optional: open the plugin settings and use `手动匹配当前歌曲` to manually choose an AMLL result or force local/default lyrics.

## Build from Source

Run:

```powershell
.\gradlew.bat --no-daemon plugin
```

Output:

```text
out\plugin\AMLL-TTML-Loader-1.0.3.zip
```

## Cache and Overrides

The plugin stores cache and manual overrides under:

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache
```

Important files:

- `raw-lyrics-index.jsonl`: cached AMLL lyric index
- `song-cache.tsv`: successful AMLL lyric matches
- `manual-overrides.tsv`: manual AMLL or local/default choices
- `miss-cache.tsv`: seven-day automatic-match miss records
- `lyric-offset-ms.txt`: global lyric offset in milliseconds
- `lyrics\*.spl`: converted lyric cache

## Logs

Runtime logs help troubleshoot plugin loading, online search, automatic matching, TTML conversion, cache reads/writes, manual matching, and fallback behavior.

Log files are saved under:

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache\logs
```

Log files are named by date, for example:

```text
amll-ttml-loader-2026-05-15.log
```

Log format example:

```text
[2026-05-15 20:30:12] [INFO] [SEARCH] Searching AMLL TTML DB: title="xxx", artist="xxx"
```

Runtime logging is enabled by default. In the plugin settings, you can:

- Enable or disable runtime logs
- Switch between normal and verbose logging
- Set the lyric offset
- Open the log directory
- Clear log files

The plugin automatically cleans up old logs and keeps either the latest seven days or the latest ten log files. Log write failures do not prevent lyrics from loading.

When reporting a bug, attach only the relevant log snippet. Do not post full logs that contain private song metadata, local paths, account information, tokens, or other sensitive data.

## Network and Privacy

- The plugin requests AMLL TTML DB using the current track title, artist, and album metadata to search for matching lyrics.
- The plugin does not upload audio files.
- The plugin does not upload user account information.
- Embedded FLAC lyric metadata is read locally only and is not uploaded.
- Lyric indexes, match results, and converted lyrics are cached locally.
- If you do not want these network requests, disable the plugin or use local/default lyrics.

## Troubleshooting

### AMLL Lyrics Are Not Loaded

Possible causes:

- The current track title, artist, or album metadata is incomplete.
- AMLL TTML DB does not include the track.
- GitHub raw or related resources are not reachable.
- The automatic match was not reliable enough, so the plugin fell back to local lyrics.
- Embedded local lyrics are not stored as FLAC Vorbis Comment, or the lyric field is not recognizable TTML/LRC/SPL text.
- A previous failed match is still in the seven-day miss cache.

Try this:

- Check the track metadata.
- Open plugin settings and use `手动匹配当前歌曲`.
- Delete the cache and try again.
- Check your network connection.
- Choose local/default lyrics as an override for the current track.
- Check the log files described in `Logs`.

## Limitations

- Salt Player's current plugin API only lets plugins provide lyrics before Salt Player loads them. The plugin cannot reliably show local lyrics first and then replace them with online lyrics during the same playback.
- Embedded lyric metadata support currently reads common FLAC Vorbis Comment fields such as `LYRICS`, `SYNCEDLYRICS`, and `UNSYNCEDLYRICS`.
- The plugin depends on AMLL TTML DB index and repository structure; upstream structure changes may temporarily break search or loading.
- Online lyric matching depends on track metadata quality.
- The plugin cannot guarantee accurate lyrics for every song.

## Third-party Notices

- [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db): lyric data source, licensed under CC0-1.0
- Salt Player for Windows: plugin runtime platform
- [spw-workshop-api](https://github.com/Moriafly/spw-workshop-api): Salt Player workshop API reference
- PF4J: plugin mechanism
- Gradle: build tool

This plugin only searches, converts, caches, and displays lyrics. Lyric content comes from AMLL TTML DB. Users should respect the rights of original music works, lyric text, and related rights holders.
