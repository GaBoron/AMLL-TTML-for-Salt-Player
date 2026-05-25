# Agent Notes

This repository contains `AMLL TTML Loader`, a Salt Player for Windows workshop plugin. Current repo-visible version is `1.0.5`.

## Current State

- The plugin searches AMLL TTML DB, converts TTML word-level lyrics to Salt Player compatible SPL-style lyrics, and falls back to local/default lyrics when online matching is unavailable or unreliable.
- Manual matching is implemented through `ManualMatcher`; it lets users edit title/artist/album, preview AMLL search results, pin a selected AMLL result, or force local/metadata lyrics for the current song.
- Lyric offset adjustment is implemented through `LyricSettings`; it supports both global offset and per-track offset in the same original Swing dialog style.
- Current-track persistence is centralized in `CurrentTrackStore`, so manual matching and per-track offset settings can still work after plugin reloads.
- Runtime logging is controlled by `PluginPreferences` and `AmllLogger`.
- README screenshots include:
  - `docs/images/lyrics-page.png`
  - `docs/images/lyric-offset-per-track-dialog.png`
  - `docs/images/manual-match-dialog.png`

## Project Shape

- Main plugin code lives under `src/main/java/dev/amll/saltplayer/ttml`.
- SPW API compile-time stubs live under `src/spwApiStubs/java`; keep them minimal and do not package them into the plugin jar.
- Declarative Salt Player settings live in `src/main/resources/preference_config.json`.
- README screenshots live under `docs/images`.
- Bundled UI fonts live under `src/main/resources/fonts`; keep the license file next to the font file.
- Gradle output uses `out/`; plugin packages are generated under `out/plugin`.

## Build And Validation

- Use JDK 21.
- In this local Windows environment, Gradle needs:

  ```powershell
  $env:JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  ```

- Validate normal changes with:

  ```powershell
  .\gradlew.bat build
  ```

- Validate release packaging with:

  ```powershell
  .\gradlew.bat plugin
  ```

- The plugin artifact is generated at `out\plugin\AMLL-TTML-Loader-<version>.zip`.
- For `1.0.5`, the expected local artifact is `out\plugin\AMLL-TTML-Loader-1.0.5.zip`.

## Release Workflow

- Releases are tag-driven. Pushing a `v*` tag triggers `.github/workflows/build.yml`, which builds the plugin and uploads the zip to the GitHub Release.
- Before pushing a release tag, run a local build/package check.
- For version bumps, update all repo-visible version references:
  - `build.gradle.kts`
  - `README.md`
  - `README.en.md`
  - `src/main/java/dev/amll/saltplayer/ttml/AmllTtmlLoader.java` User-Agent
- After the tag workflow completes, verify the GitHub Release asset exists and patch the release body if it is empty or incomplete.

## Public Documentation Policy

- `README.md`, `README.en.md`, and GitHub Release notes are public-facing documents for users and outside contributors.
- Do not include local-machine paths, personal environment details, local validation phrasing, temporary files, or Codex workflow details in public README or release notes.
- Public build instructions should stay generic: require JDK 21 and show Gradle commands, but do not mention this machine's `JAVA_HOME`.
- Release notes should describe user-visible changes and the downloadable asset, not local build steps such as "本地通过" or machine-specific verification.

## Runtime Data

The plugin stores cache, offsets, overrides, and logs under:

```text
%APPDATA%\Salt Player for Windows\workshop\amll-ttml-loader-cache
```

Important files:

- `raw-lyrics-index.jsonl`: AMLL lyric index cache.
- `song-cache.tsv`: successful AMLL match records.
- `manual-overrides.tsv`: per-track manual AMLL/local override records.
- `miss-cache.tsv`: seven-day automatic-match miss records.
- `current-media.tsv`: persisted current media snapshot used by settings dialogs.
- `lyric-offset-ms.txt`: global lyric offset in milliseconds.
- `lyric-offsets.tsv`: per-track lyric offsets in milliseconds.
- `lyrics\*.spl`: converted lyric cache.
- `logs\*.log`: runtime logs.

## UI Notes

- Swing dialogs should use the shared `Win11Swing` helper instead of raw `JOptionPane` or default Swing styling.
- Use the bundled Noto Sans CJK font for dialog UI so Chinese, Japanese, and Latin text render consistently.
- The title-bar close button is vector-painted by `Win11Swing.CloseButton`; do not replace it with a literal `X` or other text glyph.
- Keep comments on non-obvious UI/platform behavior, especially where SPW API callbacks, Swing threading, or resource loading are involved.

## Settings Notes

- `preference_config.json` supports SPW-rendered `switch`, `list`, `button`, `seekbar`, and `edittext` controls.
- Prefer explicit controls over multiple ambiguous action buttons. Runtime logging should stay a `switch`, and log level should stay a `list`.
- Static `on_click` methods referenced by `preference_config.json` must remain public static no-argument methods.

## Current-Track Handling

- `AmllLyricsExtension` calls `ManualMatcher.setCurrentMediaItem` from `updateLyrics`, `onBeforeLoadLyrics`, and `onAfterLoadLyrics` compatibility flows.
- `ManualMatcher.setCurrentMediaItem` delegates to `CurrentTrackStore.set`.
- The current media snapshot is intentionally persisted so settings entries can still work after plugin reloads.
- Per-track lyric offsets are keyed by `AmllTtmlLoader.cacheKey(mediaItem)`.
- When offset settings change, clear the relevant `AmllTtmlLoader` memory cache entry so old shifted lyrics are not reused.

## Screenshot Policy

- Do not remove existing README screenshots unless the new image represents the same feature.
- If a new feature has no existing screenshot, add a new image and README section instead of overwriting an unrelated screenshot.
- For README cache busting, prefer a new screenshot filename when replacing a GitHub-rendered image that may stay cached.
- For the current README, keep:
  - automatic lyric loading screenshot
  - lyric offset dialog screenshot using `docs/images/lyric-offset-per-track-dialog.png`
  - manual matching dialog screenshot

## Useful Tools

- Use `rg` for repo searches.
- Use `git diff`, `git status`, and focused file reads before editing.
- Use `.\gradlew.bat build` for compile validation.
- Use `.\gradlew.bat plugin` for release package validation.
- Use `gh run watch` and `gh release view` to verify tag-driven GitHub release publication.
