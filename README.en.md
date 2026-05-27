# AMLL TTML Loader for Salt Player

[简体中文](README.md) | **English**

AMLL TTML Loader is a Salt Player for Windows workshop plugin. It searches AMLL TTML DB for the current track, loads and converts word-level TTML lyrics, and falls back to local lyrics or Salt Player's default lyric flow when online matching is unreliable, times out, or is unavailable.

Current version: `1.0.5`

## Screenshots

### Automatically Loaded AMLL Lyrics

![Automatically loaded AMLL lyrics](docs/images/lyrics-page.png)

### Lyric Offset Dialog

![Lyric offset dialog](docs/images/lyric-offset-per-track-dialog.png)

### Manual Matching Dialog

![Manual matching dialog](docs/images/manual-match-dialog.png)

## For Users

### Requirements

- Windows
- Salt Player for Windows
- Access to AMLL TTML DB and GitHub raw resources

Regular users do not need JDK. JDK 21 is only required when building from source.

### Installation

1. Download `AMLL-TTML-Loader-1.0.5.zip` from the latest GitHub Release.
2. Copy the zip file to:

   ```text
   %APPDATA%\Salt Player for Windows\workshop\plugins
   ```

3. Restart Salt Player for Windows.

### Plugin Settings

In Salt Player's plugin settings, you can:

- Manually match the current track, choose an AMLL result, or force local/metadata lyrics.
- Adjust lyric offsets globally or for the current track only.
- Enable or disable runtime logs.
- Switch between normal and verbose logging.
- Open or clear the log directory.

### Lyric Offset Rules

- `Global lyric offset` applies to every track without a per-track offset.
- `Current track offset` applies only to the current track.
- If the current track has no per-track offset, the global offset is used.
- Positive values delay lyrics; negative values show lyrics earlier. Values are in milliseconds.
- Replay the current track after saving an offset to see the effect.

## Features

### Online Matching

- Search AMLL TTML DB using the current track title, artist, and album metadata.
- Use title-first automatic matching to reduce incorrect matches for same-name tracks, alternate versions, and poor metadata.
- Wait up to 10 seconds for online search, then fall back on failure, timeout, or unreliable matches.
- Record automatic-match misses for seven days, then retry after the miss cache expires.

### Lyric Conversion and Display

- Convert TTML word-level lyrics into Salt Player compatible SPL-style lyrics.
- Handle main lyrics, duet/agent lines, translations, romanization, and background vocals.
- Hide embedded lyric metadata lines such as `[ti:xxx]` and `[ar:xxx]`.
- Show a compact source line: `来源：AMLL` or `来源：本地`.

### Local Fallbacks

- Support sidecar `.ttml`, `.lrc`, and `.spl` lyric files.
- Support TTML/LRC/SPL lyrics embedded in FLAC metadata.
- Recover complete lyric lines from truncated embedded local TTML metadata where possible.

### Manual Matching

- Edit the current track title, artist, and album before searching.
- Preview candidate AMLL lyrics.
- Pin the current track to a selected AMLL result.
- Pin the current track to local/metadata lyrics.

## Local Data

The plugin stores cache, settings, and logs under:

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache
```

Important files:

- `raw-lyrics-index.jsonl`: cached AMLL lyric index
- `song-cache.tsv`: successful AMLL lyric matches
- `manual-overrides.tsv`: manual AMLL or local/default choices
- `miss-cache.tsv`: seven-day automatic-match miss records
- `lyric-offset-ms.txt`: global lyric offset in milliseconds
- `lyric-offsets.tsv`: per-track lyric offsets in milliseconds
- `lyrics\*.spl`: converted lyric cache
- `logs\*.log`: runtime logs

Log format example:

```text
[2026-05-15 20:30:12] [INFO] [SEARCH] Searching AMLL TTML DB: title="xxx", artist="xxx"
```

The plugin automatically cleans up old logs and keeps either the latest seven days or the latest ten log files. Log write failures do not prevent lyrics from loading.

When reporting a bug, attach only the relevant log snippet. Do not post full logs that contain private data.

## Troubleshooting

### AMLL Lyrics Are Not Loaded

Possible causes:

- The current track title, artist, or album metadata is incomplete.
- AMLL TTML DB does not include the track.
- GitHub raw or related resources are unreachable.
- The automatic match was not reliable enough, so the plugin fell back to local lyrics.
- Embedded local lyrics are not stored as FLAC Vorbis Comment, or the lyric field is not recognizable TTML/LRC/SPL text.
- A previous failed match is still in the seven-day miss cache.

Try this:

- Check the track metadata.
- Open plugin settings and use `手动匹配当前歌曲`.
- Delete the cache and try again.
- Check the network connection.
- Choose local/default lyrics as an override for the current track.
- Check the runtime logs.

## Development

### Project Layout

- `src/main/java/dev/amll/saltplayer/ttml`: main plugin code
- `src/spwApiStubs/java`: SPW API compile-time stubs, used for local compilation only and excluded from the plugin jar
- `src/main/resources/preference_config.json`: Salt Player plugin settings declaration
- `src/main/resources/fonts`: bundled dialog UI font and font license
- `docs/images`: README screenshots
- `out/plugin`: plugin zip generated by Gradle's `plugin` task

### Build from Source

Development requires JDK 21. Make sure `JAVA_HOME` and `PATH` point to a working JDK.

Validate normal changes:

```powershell
.\gradlew.bat build
```

Build the plugin package:

```powershell
.\gradlew.bat plugin
```

Output:

```text
out\plugin\AMLL-TTML-Loader-1.0.5.zip
```

### Release

- The version comes from `build.gradle.kts`.
- The User-Agent version is in `src/main/java/dev/amll/saltplayer/ttml/AmllTtmlLoader.java`.
- Pushing a `v*` tag triggers `.github/workflows/build.yml`; GitHub Actions builds the plugin and uploads the Release asset.

## Network and Privacy

- The plugin requests AMLL TTML DB using the current track title, artist, and album metadata to search for matching lyrics.
- The plugin does not upload audio files.
- The plugin does not upload user account information.
- Embedded FLAC lyrics are read locally only and are not uploaded.
- Lyric indexes, match results, offset settings, and converted lyrics are cached locally.
- If you do not want network requests, disable the plugin or use local/default lyrics.

## Limitations

- Salt Player's current plugin API only lets plugins provide lyrics before Salt Player loads them. The plugin cannot reliably show local lyrics first and then replace them with online lyrics during the same playback.
- Embedded lyric metadata support currently reads common FLAC Vorbis Comment fields such as `LYRICS`, `SYNCEDLYRICS`, and `UNSYNCEDLYRICS`.
- The plugin depends on AMLL TTML DB index and repository structure; upstream structure changes may temporarily break search or loading.
- Online lyric matching depends on track metadata quality.
- The plugin cannot guarantee accurate lyrics for every song.

## Third-party Notices

- [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db): lyric data source, licensed under CC0-1.0
- Salt Player for Windows: plugin runtime platform
- [spw-workshop-api](https://github.com/Moriafly/spw-workshop-api): Salt Player workshop API reference, licensed under Apache-2.0; this repository only includes compile-time stubs, which are not packaged into the plugin
- [Noto Sans CJK](https://github.com/notofonts/noto-cjk): bundled dialog UI font, licensed under OFL-1.1; the font license is distributed with the plugin package
- [PF4J](https://github.com/pf4j/pf4j): plugin mechanism API reference, licensed under Apache-2.0; this repository only includes compile-time stubs, which are not packaged into the plugin
- Gradle: build tool and Gradle Wrapper, licensed under Apache-2.0

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the full third-party license notice.

This plugin only searches, converts, caches, and displays lyrics. Lyric content comes from AMLL TTML DB. Users should respect the rights of original music works, lyric text, and related rights holders.
