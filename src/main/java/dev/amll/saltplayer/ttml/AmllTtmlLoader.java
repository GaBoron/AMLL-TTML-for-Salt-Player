package dev.amll.saltplayer.ttml;

import com.xuncorp.spw.workshop.api.PlaybackExtensionPoint;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

final class AmllTtmlLoader {
    private static final String CACHE_VERSION = "v3";
    private static final Duration INDEX_CACHE_MAX_AGE = Duration.ofHours(1);
    private static final Duration LYRICS_CACHE_MAX_AGE = Duration.ofHours(1);
    private static final String SOURCE_PREFIX = "\u6765\u6e90\uff1a";
    private static final String SOURCE_AMLL = "AMLL";
    private static final String OVERRIDE_LOCAL = "__LOCAL__";
    private static final String SOURCE_LOCAL = "\u672c\u5730";
    private static final Duration MISS_CACHE_MAX_AGE = Duration.ofHours(24);
    private static final String INDEX_URL =
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/metadata/raw-lyrics-index.jsonl";
    private static final String RAW_LYRICS_BASE_URL =
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/raw-lyrics/";

    private final Path cacheRoot;
    private final Path indexCache;
    private final Path songCache;
    private final Path lyricsCache;
    private final Path overrideCache;
    private final Path missCache;

    AmllTtmlLoader() {
        this(defaultCacheRoot());
    }

    AmllTtmlLoader(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
        this.indexCache = cacheRoot.resolve("raw-lyrics-index.jsonl");
        this.songCache = cacheRoot.resolve("song-cache.tsv");
        this.lyricsCache = cacheRoot.resolve("lyrics");
        this.overrideCache = cacheRoot.resolve("manual-overrides.tsv");
        this.missCache = cacheRoot.resolve("miss-cache.tsv");
    }

    LoadResult load(PlaybackExtensionPoint.MediaItem mediaItem) {
        try {
            Files.createDirectories(cacheRoot);
            Files.createDirectories(lyricsCache);

            String songKey = cacheKey(mediaItem);
            String override = readOverride(songKey);
            if (OVERRIDE_LOCAL.equals(override)) return null;
            if (override != null && override.endsWith(".ttml")) {
                return loadRawLyric(mediaItem, songKey, override);
            }
            if (hasRecentMiss(songKey)) return loadLocalLyrics(mediaItem).orElse(null);

            String cached = readCachedLyrics(songKey);
            if (cached != null) return new LoadResult(withSourceTag(cached, SOURCE_AMLL), SOURCE_AMLL);

            IndexEntry match = findBestMatch(mediaItem);
            if (match == null) {
                refreshIndex();
                match = findBestMatch(mediaItem);
            }
            if (match == null) {
                writeMiss(songKey);
                return loadLocalLyrics(mediaItem).orElse(null);
            }

            String ttml = fetchText(RAW_LYRICS_BASE_URL + match.rawLyricFile);
            String spl = TtmlToSplConverter.convert(ttml, mediaItem);
            if (spl.isBlank()) {
                writeMiss(songKey);
                return loadLocalLyrics(mediaItem).orElse(null);
            }

            writeCachedLyrics(songKey, match.rawLyricFile, spl);
            return new LoadResult(withSourceTag(spl, SOURCE_AMLL), SOURCE_AMLL);
        } catch (Exception error) {
            System.out.println("AMLL TTML Loader failed: " + error.getMessage());
            try {
                return loadLocalLyrics(mediaItem).orElse(null);
            } catch (Exception localError) {
                System.out.println("AMLL TTML Loader local fallback failed: " + localError.getMessage());
                return null;
            }
        }
    }

    LoadResult loadRawLyric(PlaybackExtensionPoint.MediaItem mediaItem, String songKey, String rawLyricFile) throws Exception {
        String ttml = fetchText(RAW_LYRICS_BASE_URL + rawLyricFile);
        String spl = TtmlToSplConverter.convert(ttml, mediaItem);
        if (spl.isBlank()) return null;
        writeCachedLyrics(songKey, rawLyricFile, spl);
        return new LoadResult(withSourceTag(spl, SOURCE_AMLL), SOURCE_AMLL);
    }

    List<SearchResult> search(String title, String artist, String album, int limit) throws Exception {
        PlaybackExtensionPoint.MediaItem query = new PlaybackExtensionPoint.MediaItem(title, artist, album, "", "");
        List<ScoredEntry> scored = new ArrayList<>();
        try (Stream<String> lines = readIndexLines()) {
            lines.filter(line -> !line.isBlank())
                    .map(IndexEntry::fromJsonLine)
                    .filter(entry -> entry.rawLyricFile.endsWith(".ttml"))
                    .forEach(entry -> {
                        int score = score(query, entry);
                        if (score >= 45) scored.add(new ScoredEntry(entry, score));
                    });
        }
        scored.sort(Comparator.comparingInt((ScoredEntry item) -> item.score).reversed());

        List<SearchResult> results = new ArrayList<>();
        for (ScoredEntry item : scored) {
            if (results.size() >= limit) break;
            IndexEntry entry = item.entry;
            results.add(new SearchResult(
                    entry.rawLyricFile,
                    first(entry.metadata.get("musicName")),
                    String.join(" / ", entry.metadata.getOrDefault("artists", List.of())),
                    first(entry.metadata.get("album")),
                    item.score
            ));
        }
        return results;
    }

    String preview(PlaybackExtensionPoint.MediaItem mediaItem, String rawLyricFile, int lines) throws Exception {
        String spl = TtmlToSplConverter.convert(fetchText(RAW_LYRICS_BASE_URL + rawLyricFile), mediaItem);
        StringBuilder preview = new StringBuilder();
        int count = 0;
        for (String line : spl.split("\\R")) {
            if (line.contains(SOURCE_PREFIX) || line.isBlank()) continue;
            preview.append(stripTimestamp(line)).append(System.lineSeparator());
            count++;
            if (count >= lines) break;
        }
        return preview.toString().trim();
    }

    void saveOverride(String songKey, String rawLyricFile) throws IOException {
        writeOverride(songKey, rawLyricFile);
        clearMiss(songKey);
    }

    void saveLocalOverride(String songKey) throws IOException {
        writeOverride(songKey, OVERRIDE_LOCAL);
        clearMiss(songKey);
    }

    private Optional<LoadResult> loadLocalLyrics(PlaybackExtensionPoint.MediaItem mediaItem) throws Exception {
        if (mediaItem.getPath() == null || mediaItem.getPath().isBlank()) return Optional.empty();

        Path audioPath = Path.of(mediaItem.getPath());
        Path parent = audioPath.getParent();
        Path fileNamePath = audioPath.getFileName();
        if (parent == null || fileNamePath == null) return Optional.empty();

        String fileName = fileNamePath.toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (stem.isBlank()) return Optional.empty();

        for (String extension : List.of(".ttml", ".lrc", ".spl")) {
            Path lyricPath = parent.resolve(stem + extension);
            if (!Files.isRegularFile(lyricPath)) continue;

            String lyrics = Files.readString(lyricPath, StandardCharsets.UTF_8);
            if (lyrics.isBlank()) continue;
            if (extension.equals(".ttml")) {
                lyrics = TtmlToSplConverter.convert(lyrics, mediaItem);
            }
            return Optional.of(new LoadResult(withSourceTag(lyrics, SOURCE_LOCAL), SOURCE_LOCAL));
        }
        return Optional.empty();
    }

    private String withSourceTag(String lyrics, String source) {
        String sourceLine = "[00:00.000]" + SOURCE_PREFIX;
        if (lyrics.startsWith(sourceLine)) {
            int lineEnd = lyrics.indexOf('\n');
            if (lineEnd >= 0) {
                return sourceLine + source + System.lineSeparator() + lyrics.substring(lineEnd + 1);
            }
            return sourceLine + source;
        }
        return sourceLine + source + System.lineSeparator() + lyrics;
    }

    private IndexEntry findBestMatch(PlaybackExtensionPoint.MediaItem mediaItem) throws IOException, InterruptedException {
        List<ScoredEntry> scored = new ArrayList<>();
        try (Stream<String> lines = readIndexLines()) {
            lines.filter(line -> !line.isBlank())
                    .map(IndexEntry::fromJsonLine)
                    .filter(entry -> entry.rawLyricFile.endsWith(".ttml"))
                    .forEach(entry -> {
                        int score = score(mediaItem, entry);
                        if (score >= 70) scored.add(new ScoredEntry(entry, score));
                    });
        }

        scored.sort(Comparator.comparingInt((ScoredEntry item) -> item.score).reversed());
        if (scored.isEmpty()) return null;

        ScoredEntry best = scored.get(0);
        ScoredEntry second = scored.size() > 1 ? scored.get(1) : null;
        if (best.score >= 105 || second == null || best.score - second.score >= 10) {
            return best.entry;
        }
        return null;
    }

    private Stream<String> readIndexLines() throws IOException, InterruptedException {
        if (!Files.isRegularFile(indexCache) || isOlderThan(indexCache, INDEX_CACHE_MAX_AGE)) {
            refreshIndex();
        }
        return Files.lines(indexCache, StandardCharsets.UTF_8);
    }

    private void refreshIndex() throws IOException, InterruptedException {
        Files.writeString(indexCache, fetchText(INDEX_URL), StandardCharsets.UTF_8);
    }

    private String readOverride(String songKey) throws IOException {
        if (!Files.isRegularFile(overrideCache)) return null;
        for (String line : Files.readAllLines(overrideCache, StandardCharsets.UTF_8)) {
            int firstTab = line.indexOf('\t');
            if (firstTab < 0 || !line.substring(0, firstTab).equals(songKey)) continue;
            String[] fields = line.split("\t");
            return fields.length >= 2 ? fields[1] : null;
        }
        return null;
    }

    private void writeOverride(String songKey, String value) throws IOException {
        Files.createDirectories(cacheRoot);
        List<String> lines = Files.isRegularFile(overrideCache)
                ? new ArrayList<>(Files.readAllLines(overrideCache, StandardCharsets.UTF_8))
                : new ArrayList<>();
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        lines.add(String.join("\t", songKey, value, Instant.now().toString(), "manual"));
        Files.write(overrideCache, lines, StandardCharsets.UTF_8);
    }

    private boolean hasRecentMiss(String songKey) throws IOException {
        if (!Files.isRegularFile(missCache)) return false;
        for (String line : Files.readAllLines(missCache, StandardCharsets.UTF_8)) {
            String[] fields = line.split("\t");
            if (fields.length < 2 || !fields[0].equals(songKey)) continue;
            try {
                return Instant.parse(fields[1]).plus(MISS_CACHE_MAX_AGE).isAfter(Instant.now());
            } catch (Exception ignored) {
                return false;
            }
        }
        return false;
    }

    private void writeMiss(String songKey) throws IOException {
        Files.createDirectories(cacheRoot);
        List<String> lines = Files.isRegularFile(missCache)
                ? new ArrayList<>(Files.readAllLines(missCache, StandardCharsets.UTF_8))
                : new ArrayList<>();
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        lines.add(songKey + "\t" + Instant.now());
        Files.write(missCache, lines, StandardCharsets.UTF_8);
    }

    private void clearMiss(String songKey) throws IOException {
        if (!Files.isRegularFile(missCache)) return;
        List<String> lines = new ArrayList<>(Files.readAllLines(missCache, StandardCharsets.UTF_8));
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        Files.write(missCache, lines, StandardCharsets.UTF_8);
    }

    private String readCachedLyrics(String songKey) throws IOException {
        if (!Files.isRegularFile(songCache)) return null;

        for (String line : Files.readAllLines(songCache, StandardCharsets.UTF_8)) {
            int firstTab = line.indexOf('\t');
            if (firstTab < 0 || !line.substring(0, firstTab).equals(songKey)) continue;
            String[] fields = line.split("\t");
            if (fields.length < 5) return null;
            if (!CACHE_VERSION.equals(fields[4])) return null;

            Path lyricPath = lyricsCache.resolve(fields[2]).normalize();
            if (!lyricPath.startsWith(lyricsCache) || !Files.isRegularFile(lyricPath)) return null;
            if (isOlderThan(lyricPath, LYRICS_CACHE_MAX_AGE)) return null;
            return Files.readString(lyricPath, StandardCharsets.UTF_8);
        }
        return null;
    }

    private void writeCachedLyrics(String songKey, String rawLyricFile, String spl) throws IOException {
        String lyricName = sha256(songKey) + ".spl";
        Files.writeString(lyricsCache.resolve(lyricName), spl, StandardCharsets.UTF_8);

        List<String> lines = Files.isRegularFile(songCache)
                ? new ArrayList<>(Files.readAllLines(songCache, StandardCharsets.UTF_8))
                : new ArrayList<>();
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        lines.add(String.join("\t", songKey, rawLyricFile, lyricName, Instant.now().toString(), CACHE_VERSION));
        Files.write(songCache, lines, StandardCharsets.UTF_8);
    }

    private String fetchText(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(25_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "salt-player-amll-ttml-loader/1.0");

        int status = connection.getResponseCode();
        if (status < 200 || status > 299) {
            throw new IOException("HTTP " + status + " for " + url);
        }
        try (InputStream stream = connection.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private int score(PlaybackExtensionPoint.MediaItem mediaItem, IndexEntry entry) {
        String title = normalize(mediaItem.getTitle());
        String album = normalize(mediaItem.getAlbum());
        List<String> artists = splitArtists(mediaItem.getArtist());

        List<String> entryTitles = entry.metadata.getOrDefault("musicName", List.of()).stream().map(AmllTtmlLoader::normalize).toList();
        List<String> entryAlbums = entry.metadata.getOrDefault("album", List.of()).stream().map(AmllTtmlLoader::normalize).toList();
        List<String> entryArtists = entry.metadata.getOrDefault("artists", List.of()).stream().flatMap(value -> splitArtists(value).stream()).toList();

        int score = 0;
        if (!title.isBlank() && entryTitles.stream().anyMatch(title::equals)) score += 85;
        if (!title.isBlank() && score == 0 && entryTitles.stream().anyMatch(value -> value.contains(title) || title.contains(value))) score += 65;
        if (!album.isBlank() && entryAlbums.stream().anyMatch(album::equals)) score += 25;
        if (!artists.isEmpty() && entryArtists.stream().anyMatch(entryArtist -> artists.stream().anyMatch(entryArtist::equals))) score += 40;
        if (!artists.isEmpty() && entryArtists.stream().anyMatch(entryArtist -> artists.stream().anyMatch(artist -> artist.contains(entryArtist) || entryArtist.contains(artist)))) score += 15;
        return score;
    }

    private boolean isOlderThan(Path path, Duration maxAge) throws IOException {
        return Files.getLastModifiedTime(path).toInstant().plus(maxAge).isBefore(Instant.now());
    }

    private static Path defaultCacheRoot() {
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank() ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("Salt Player for Windows").resolve("workshop").resolve("amll-ttml-loader-cache");
    }

    static String cacheKey(PlaybackExtensionPoint.MediaItem item) {
        return String.join("|", normalize(item.getTitle()), normalize(item.getArtist()), normalize(item.getAlbum()));
    }

    private static String first(List<String> values) {
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private static String stripTimestamp(String line) {
        return line.replaceAll("\\[[0-9]{2}:[0-9]{2}\\.[0-9]{2,3}]", "")
                .replaceAll("<[0-9]{2}:[0-9]{2}\\.[0-9]{2,3}>", "")
                .trim();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)|\\uFF08[^\\uFF09]*\\uFF09|\\[[^]]*]", "")
                .replaceAll("[^\\p{L}\\p{N}]+", "")
                .trim();
    }

    private static List<String> splitArtists(String value) {
        if (value == null || value.isBlank()) return List.of();
        String[] parts = value.split("(?i)\\s*(?:/|\\uFF0F|&|\\u3001|,|;|\\uFF1B| feat\\. | ft\\. )\\s*");
        List<String> artists = new ArrayList<>();
        for (String part : parts) {
            String normalized = normalize(part);
            if (!normalized.isBlank()) artists.add(normalized);
        }
        return artists;
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private record ScoredEntry(IndexEntry entry, int score) {
    }

    record SearchResult(String rawLyricFile, String title, String artist, String album, int score) {
        @Override
        public String toString() {
            return title + " - " + artist + " [" + album + "]  #" + score;
        }
    }
}
