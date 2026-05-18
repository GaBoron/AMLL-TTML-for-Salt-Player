package com.xuncorp.spw.workshop.api;

import org.pf4j.ExtensionPoint;

import java.util.List;

public interface PlaybackExtensionPoint extends ExtensionPoint {
    default String updateLyrics(MediaItem mediaItem) {
        return null;
    }

    default String onBeforeLoadLyrics(MediaItem mediaItem) {
        return null;
    }

    default String onAfterLoadLyrics(MediaItem mediaItem) {
        return null;
    }

    final class MediaItem {
        private final String title;
        private final String artist;
        private final String album;
        private final String albumArtist;
        private final String path;

        public MediaItem(String title, String artist, String album, String albumArtist, String path) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.albumArtist = albumArtist;
            this.path = path;
        }

        public String getTitle() {
            return title;
        }

        public String getArtist() {
            return artist;
        }

        public String getAlbum() {
            return album;
        }

        public String getAlbumArtist() {
            return albumArtist;
        }

        public String getPath() {
            return path;
        }
    }

    final class LyricsLine {
        public LyricsLine(long startTime, long endTime, List<Cell> lyricsCells, String pureMainText, String pureSubText) {
        }

        public static final class Cell {
            public Cell(long startTime, long endTime, String text) {
            }
        }
    }
}
