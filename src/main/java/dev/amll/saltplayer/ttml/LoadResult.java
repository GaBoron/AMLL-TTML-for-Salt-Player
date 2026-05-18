package dev.amll.saltplayer.ttml;

/**
 * 歌词加载结果，包含最终返回给播放器的歌词文本和来源标记。
 */
final class LoadResult {
    final String lyrics;
    final String source;

    LoadResult(String lyrics, String source) {
        this.lyrics = lyrics;
        this.source = source;
    }
}
