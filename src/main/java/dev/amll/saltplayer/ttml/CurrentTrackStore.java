package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

final class CurrentTrackStore {
    private static final Path CACHE_ROOT = defaultCacheRoot();
    private static final Path CURRENT_MEDIA_FILE = CACHE_ROOT.resolve("current-media.tsv");
    private static volatile PlaybackExtensionPoint.MediaItem currentMediaItem;

    private CurrentTrackStore() {
    }

    static void set(PlaybackExtensionPoint.MediaItem mediaItem) {
        currentMediaItem = mediaItem;
        save(mediaItem);
    }

    static PlaybackExtensionPoint.MediaItem get() {
        PlaybackExtensionPoint.MediaItem mediaItem = currentMediaItem != null ? currentMediaItem : load();
        if (mediaItem != null) currentMediaItem = mediaItem;
        return mediaItem;
    }

    private static void save(PlaybackExtensionPoint.MediaItem mediaItem) {
        if (mediaItem == null) return;
        try {
            Files.createDirectories(CACHE_ROOT);
            // 使用 Base64 存储，避免标题或路径中的制表符、换行破坏快照格式。
            String line = String.join("\t",
                    encode(mediaItem.getTitle()),
                    encode(mediaItem.getArtist()),
                    encode(mediaItem.getAlbum()),
                    encode(mediaItem.getAlbumArtist()),
                    encode(mediaItem.getPath())
            );
            Files.writeString(CURRENT_MEDIA_FILE, line, StandardCharsets.UTF_8);
        } catch (Exception error) {
            AmllLogger.warn("CONFIG", "Failed to persist current media snapshot.");
        }
    }

    private static PlaybackExtensionPoint.MediaItem load() {
        try {
            if (!Files.isRegularFile(CURRENT_MEDIA_FILE)) return null;
            String[] fields = Files.readString(CURRENT_MEDIA_FILE, StandardCharsets.UTF_8).trim().split("\t", -1);
            if (fields.length != 5) return null;
            PlaybackExtensionPoint.MediaItem mediaItem = new PlaybackExtensionPoint.MediaItem(
                    decode(fields[0]),
                    decode(fields[1]),
                    decode(fields[2]),
                    decode(fields[3]),
                    decode(fields[4])
            );
            return isUsable(mediaItem) ? mediaItem : null;
        } catch (Exception error) {
            AmllLogger.warn("CONFIG", "Failed to read current media snapshot.");
            return null;
        }
    }

    private static boolean isUsable(PlaybackExtensionPoint.MediaItem mediaItem) {
        return mediaItem != null
                && (!blank(mediaItem.getTitle()) || !blank(mediaItem.getArtist()) || !blank(mediaItem.getPath()));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }
}
