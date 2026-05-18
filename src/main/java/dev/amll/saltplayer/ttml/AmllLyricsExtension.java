package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;
import org.pf4j.Extension;

/**
 * Salt Player 歌词加载扩展入口，负责把播放器回调转交给 AMLL 加载器。
 */
@Extension
public final class AmllLyricsExtension implements PlaybackExtensionPoint {
    private final AmllTtmlLoader loader = new AmllTtmlLoader();

    @Override
    public String onBeforeLoadLyrics(PlaybackExtensionPoint.MediaItem mediaItem) {
        ManualMatcher.setCurrentMediaItem(mediaItem);
        AmllLogger.info("INIT", "Loading lyrics for current track: title=\"" + AmllLogger.safeText(mediaItem.getTitle())
                + "\", artist=\"" + AmllLogger.safeText(mediaItem.getArtist())
                + "\", album=\"" + AmllLogger.safeText(mediaItem.getAlbum()) + "\".");
        LoadResult result = loader.load(mediaItem);
        if (result != null) {
            AmllLogger.info("INIT", "Lyrics loaded from source: " + result.source + ".");
            return result.lyrics;
        }
        AmllLogger.warn("FALLBACK", "No plugin lyrics returned; Salt Player will continue with its default behavior.");
        return null;
    }
}
