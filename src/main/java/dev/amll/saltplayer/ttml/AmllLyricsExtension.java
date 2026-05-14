package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;
import org.pf4j.Extension;

@Extension
public final class AmllLyricsExtension implements PlaybackExtensionPoint {
    private final AmllTtmlLoader loader = new AmllTtmlLoader();

    @Override
    public String onBeforeLoadLyrics(PlaybackExtensionPoint.MediaItem mediaItem) {
        ManualMatcher.setCurrentMediaItem(mediaItem);
        LoadResult result = loader.load(mediaItem);
        if (result != null) {
            return result.lyrics;
        }
        return null;
    }
}
