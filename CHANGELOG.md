# Changelog

## 1.0.2 - 2026-05-17

- 支持读取 FLAC Vorbis Comment 中的本地内嵌歌词元数据。
- 支持将内嵌 `LYRICS`/`SYNCEDLYRICS` 等字段中的 TTML 歌词转换为 Salt Player 可用歌词。
- 当本地内嵌 TTML 元数据被截断导致 XML 不完整时，尽量恢复已完整闭合的歌词行。
- 更新文档中的安装包名称和本地歌词说明。

## 1.0.1 - 2026-05-15

- Added runtime log functionality.
- Added log-related plugin settings.
- Supports saving key information during search, matching, transformation, caching, and fallback processes.
- Supports automatic cleanup of old logs.
- Improved log descriptions in the README.
- Corrected the plugin's open-source address and project documentation.

## 1.0.0 - 2026-05-14

- Initial release.
- Supports AMLL TTML DB search.
- Supports TTML to Salt Player SPL style lyrics conversion.
- Supports automatic matching, caching, failure caching, and manual matching.
- Supports local/default lyrics rollback.
