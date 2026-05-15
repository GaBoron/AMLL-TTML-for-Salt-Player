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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

final class AmllTtmlLoader {
    private static final String CACHE_VERSION = "v3";
    private static final Duration INDEX_CACHE_MAX_AGE = Duration.ofHours(1);
    private static final Duration ONLINE_SEARCH_TIMEOUT = Duration.ofSeconds(10);
    private static final String SOURCE_PREFIX = "\u6765\u6e90\uff1a";
    private static final String SOURCE_AMLL = "AMLL";
    private static final String OVERRIDE_LOCAL = "__LOCAL__";
    private static final String SOURCE_LOCAL = "\u672c\u5730";
    private static final Duration MISS_CACHE_MAX_AGE = Duration.ofDays(7);
    private static final ExecutorService ONLINE_SEARCH_EXECUTOR = Executors.newFixedThreadPool(2, new DaemonThreadFactory());
    private static final ConcurrentMap<String, LoadResult> MEMORY_RESULT_CACHE = new ConcurrentHashMap<>();
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
            AmllLogger.verbose("CACHE", "Cache root prepared. Logs: " + AmllLogger.logRoot());

            String songKey = cacheKey(mediaItem);
            String override = readOverride(songKey);
            if (OVERRIDE_LOCAL.equals(override)) {
                AmllLogger.info("MANUAL", "Manual local/default override hit.");
                AmllLogger.info("FALLBACK", "Falling back because the current track is manually set to local/default lyrics.");
                return null;
            }
            if (override != null && override.endsWith(".ttml")) {
                AmllLogger.info("MANUAL", "Manual AMLL override hit: " + override + ".");
                String cached = readCachedLyrics(songKey, override);
                if (cached != null) {
                    AmllLogger.info("CACHE", "Loaded manually selected AMLL lyrics from cache.");
                    return remember(songKey, new LoadResult(withSourceTag(cached, SOURCE_AMLL), SOURCE_AMLL));
                }
                return remember(songKey, loadRawLyric(mediaItem, songKey, override));
            }

            LoadResult memoryResult = MEMORY_RESULT_CACHE.get(songKey);
            if (memoryResult != null && SOURCE_AMLL.equals(memoryResult.source)) {
                AmllLogger.info("CACHE", "Loaded AMLL lyrics from memory cache.");
                return memoryResult;
            }

            String cached = readCachedLyrics(songKey);
            if (cached != null) {
                AmllLogger.info("CACHE", "Loaded AMLL lyrics from converted lyric cache.");
                return remember(songKey, new LoadResult(withSourceTag(cached, SOURCE_AMLL), SOURCE_AMLL));
            }

            if (memoryResult != null) {
                AmllLogger.info("CACHE", "Loaded previous fallback result from memory cache.");
                return memoryResult;
            }

            if (hasRecentMiss(songKey)) {
                AmllLogger.info("CACHE", "Recent miss cache hit; skipping automatic online match.");
                return rememberLocalFallback(mediaItem, songKey, "recent miss cache");
            }
            return loadOnlineWithTimeout(mediaItem, songKey);
        } catch (Exception error) {
            AmllLogger.error("FALLBACK", "AMLL lyric loading failed; trying local/default fallback.", error);
            try {
                return remember(cacheKey(mediaItem), loadLocalLyrics(mediaItem).orElse(null));
            } catch (Exception localError) {
                AmllLogger.error("FALLBACK", "Local/default fallback failed.", localError);
                return null;
            }
        }
    }

    private LoadResult loadOnlineWithTimeout(PlaybackExtensionPoint.MediaItem mediaItem, String songKey) throws Exception {
        var future = ONLINE_SEARCH_EXECUTOR.submit((Callable<LoadResult>) () -> loadOnlineLyrics(mediaItem, songKey));
        try {
            LoadResult result = future.get(ONLINE_SEARCH_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return result != null ? remember(songKey, result) : rememberLocalFallback(mediaItem, songKey, "no reliable AMLL match");
        } catch (TimeoutException timeout) {
            AmllLogger.warn("SEARCH", "Online search timed out after " + ONLINE_SEARCH_TIMEOUT.toSeconds() + " seconds.");
            writeMiss(songKey);
            return rememberLocalFallback(mediaItem, songKey, "online search timeout");
        } catch (Exception error) {
            AmllLogger.error("SEARCH", "Online search failed.", error);
            writeMiss(songKey);
            return rememberLocalFallback(mediaItem, songKey, "online search failure");
        }
    }

    private LoadResult rememberLocalFallback(PlaybackExtensionPoint.MediaItem mediaItem, String songKey, String reason) throws Exception {
        AmllLogger.info("FALLBACK", "Using local/default lyrics. Reason: " + reason + ".");
        return remember(songKey, loadLocalLyrics(mediaItem).orElse(null));
    }

    private LoadResult remember(String songKey, LoadResult result) {
        if (result != null) {
            MEMORY_RESULT_CACHE.put(songKey, result);
        }
        return result;
    }

    private LoadResult loadOnlineLyrics(PlaybackExtensionPoint.MediaItem mediaItem, String songKey) throws Exception {
        AmllLogger.info("SEARCH", "Searching AMLL TTML DB: title=\"" + AmllLogger.safeText(mediaItem.getTitle())
                + "\", artist=\"" + AmllLogger.safeText(mediaItem.getArtist())
                + "\", album=\"" + AmllLogger.safeText(mediaItem.getAlbum()) + "\".");
        IndexEntry match = findBestMatch(mediaItem);
        if (match == null) {
            AmllLogger.info("SEARCH", "No reliable match from cached index; refreshing AMLL index.");
            refreshIndex();
            match = findBestMatch(mediaItem);
        }
        if (match == null) {
            AmllLogger.info("MATCH", "Automatic match failed or score gap was not reliable enough.");
            writeMiss(songKey);
            return null;
        }

        AmllLogger.info("MATCH", "Automatic AMLL match selected: " + match.rawLyricFile + ".");
        String ttml = fetchText(RAW_LYRICS_BASE_URL + match.rawLyricFile);
        AmllLogger.info("SEARCH", "Downloaded TTML lyric: " + match.rawLyricFile + ".");
        String spl = TtmlToSplConverter.convert(ttml, mediaItem);
        if (spl.isBlank()) {
            AmllLogger.warn("CONVERT", "TTML conversion returned empty lyrics for " + match.rawLyricFile + ".");
            writeMiss(songKey);
            return null;
        }
        AmllLogger.info("CONVERT", "Converted TTML to Salt Player SPL-style lyrics.");

        writeCachedLyrics(songKey, match.rawLyricFile, spl);
        return new LoadResult(withSourceTag(spl, SOURCE_AMLL), SOURCE_AMLL);
    }

    LoadResult loadRawLyric(PlaybackExtensionPoint.MediaItem mediaItem, String songKey, String rawLyricFile) throws Exception {
        AmllLogger.info("MANUAL", "Loading manually selected TTML lyric: " + rawLyricFile + ".");
        String ttml = fetchText(RAW_LYRICS_BASE_URL + rawLyricFile);
        AmllLogger.info("SEARCH", "Downloaded manually selected TTML lyric: " + rawLyricFile + ".");
        String spl = TtmlToSplConverter.convert(ttml, mediaItem);
        if (spl.isBlank()) {
            AmllLogger.warn("CONVERT", "Manual TTML conversion returned empty lyrics for " + rawLyricFile + ".");
            return null;
        }
        AmllLogger.info("CONVERT", "Converted manually selected TTML lyric.");
        writeCachedLyrics(songKey, rawLyricFile, spl);
        return new LoadResult(withSourceTag(spl, SOURCE_AMLL), SOURCE_AMLL);
    }

    List<SearchResult> search(String title, String artist, String album, int limit) throws Exception {
        AmllLogger.info("MANUAL", "Manual search requested: title=\"" + AmllLogger.safeText(title)
                + "\", artist=\"" + AmllLogger.safeText(artist)
                + "\", album=\"" + AmllLogger.safeText(album) + "\".");
        PlaybackExtensionPoint.MediaItem query = new PlaybackExtensionPoint.MediaItem(title, artist, album, "", "");
        List<ScoredEntry> scored = new ArrayList<>();
        try (Stream<String> lines = readIndexLines()) {
            lines.filter(line -> !line.isBlank())
                    .map(IndexEntry::fromJsonLine)
                    .filter(entry -> entry.rawLyricFile.endsWith(".ttml"))
                    .forEach(entry -> {
                        MatchScore matchScore = score(query, entry);
                        if (matchScore.titleSimilarity >= 0.60 && matchScore.score >= 80) {
                            scored.add(new ScoredEntry(entry, matchScore.score, matchScore.titleSimilarity));
                        }
                    });
        }
        scored.sort(Comparator.comparingInt((ScoredEntry item) -> item.score).reversed()
                .thenComparing(Comparator.comparingDouble((ScoredEntry item) -> item.titleSimilarity).reversed()));

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
        AmllLogger.info("MANUAL", "Manual search returned " + results.size() + " result(s).");
        return results;
    }

    String preview(PlaybackExtensionPoint.MediaItem mediaItem, String rawLyricFile, int lines) throws Exception {
        AmllLogger.info("MANUAL", "Loading manual preview for " + rawLyricFile + ".");
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
        MEMORY_RESULT_CACHE.remove(songKey);
        writeOverride(songKey, rawLyricFile);
        clearMiss(songKey);
        AmllLogger.info("MANUAL", "Saved manual AMLL override: " + rawLyricFile + ".");
    }

    void saveLocalOverride(String songKey) throws IOException {
        MEMORY_RESULT_CACHE.remove(songKey);
        writeOverride(songKey, OVERRIDE_LOCAL);
        clearMiss(songKey);
        AmllLogger.info("MANUAL", "Saved manual local/default override.");
    }

    private Optional<LoadResult> loadLocalLyrics(PlaybackExtensionPoint.MediaItem mediaItem) throws Exception {
        if (mediaItem.getPath() == null || mediaItem.getPath().isBlank()) {
            AmllLogger.info("FALLBACK", "No local audio path is available for local lyric lookup.");
            return Optional.empty();
        }

        Path audioPath = Path.of(mediaItem.getPath());
        Path parent = audioPath.getParent();
        Path fileNamePath = audioPath.getFileName();
        if (parent == null || fileNamePath == null) {
            AmllLogger.info("FALLBACK", "Local lyric lookup skipped because the audio path has no parent or file name.");
            return Optional.empty();
        }

        String fileName = fileNamePath.toString();
        int dot = fileName.lastIndexOf('.');
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        if (stem.isBlank()) {
            AmllLogger.info("FALLBACK", "Local lyric lookup skipped because the audio file name is blank.");
            return Optional.empty();
        }

        for (String extension : List.of(".ttml", ".lrc", ".spl")) {
            Path lyricPath = parent.resolve(stem + extension);
            if (!Files.isRegularFile(lyricPath)) continue;

            String lyrics = Files.readString(lyricPath, StandardCharsets.UTF_8);
            if (lyrics.isBlank()) continue;
            if (extension.equals(".ttml")) {
                AmllLogger.info("CONVERT", "Converting local TTML fallback lyrics.");
                lyrics = TtmlToSplConverter.convert(lyrics, mediaItem);
            }
            AmllLogger.info("FALLBACK", "Loaded local fallback lyric file with extension " + extension + ".");
            return Optional.of(new LoadResult(withSourceTag(lyrics, SOURCE_LOCAL), SOURCE_LOCAL));
        }
        AmllLogger.info("FALLBACK", "No local fallback lyric file found beside the current track.");
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
                        MatchScore matchScore = score(mediaItem, entry);
                        if (matchScore.titleSimilarity >= 0.82 && matchScore.score >= 135) {
                            scored.add(new ScoredEntry(entry, matchScore.score, matchScore.titleSimilarity));
                        }
                    });
        }

        scored.sort(Comparator.comparingInt((ScoredEntry item) -> item.score).reversed()
                .thenComparing(Comparator.comparingDouble((ScoredEntry item) -> item.titleSimilarity).reversed()));
        if (scored.isEmpty()) {
            AmllLogger.info("MATCH", "No candidate passed automatic match thresholds.");
            return null;
        }

        ScoredEntry best = scored.get(0);
        ScoredEntry second = scored.size() > 1 ? scored.get(1) : null;
        AmllLogger.info("MATCH", "Best automatic candidate score=" + best.score
                + ", titleSimilarity=" + String.format(Locale.ROOT, "%.3f", best.titleSimilarity)
                + ", file=" + best.entry.rawLyricFile + ".");
        if (second == null || best.score - second.score >= 18 || best.score >= 175 && best.score - second.score >= 8) {
            return best.entry;
        }
        AmllLogger.info("MATCH", "Best candidate rejected because score gap was too small. secondScore=" + second.score + ".");
        return null;
    }

    private Stream<String> readIndexLines() throws IOException, InterruptedException {
        if (!Files.isRegularFile(indexCache) || isOlderThan(indexCache, INDEX_CACHE_MAX_AGE)) {
            refreshIndex();
        }
        return Files.lines(indexCache, StandardCharsets.UTF_8);
    }

    private void refreshIndex() throws IOException, InterruptedException {
        AmllLogger.info("SEARCH", "Refreshing AMLL TTML DB index.");
        Files.writeString(indexCache, fetchText(INDEX_URL), StandardCharsets.UTF_8);
        AmllLogger.info("SEARCH", "AMLL TTML DB index refreshed.");
    }

    private String readOverride(String songKey) throws IOException {
        if (!Files.isRegularFile(overrideCache)) {
            AmllLogger.verbose("CACHE", "Manual override cache does not exist.");
            return null;
        }
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
        AmllLogger.info("CACHE", "Manual override cache updated.");
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
        AmllLogger.info("CACHE", "Miss cache updated.");
    }

    private void clearMiss(String songKey) throws IOException {
        if (!Files.isRegularFile(missCache)) return;
        List<String> lines = new ArrayList<>(Files.readAllLines(missCache, StandardCharsets.UTF_8));
        lines.removeIf(line -> line.startsWith(songKey + "\t"));
        Files.write(missCache, lines, StandardCharsets.UTF_8);
        AmllLogger.info("CACHE", "Miss cache cleared for current track.");
    }

    private String readCachedLyrics(String songKey) throws IOException {
        return readCachedLyrics(songKey, null);
    }

    private String readCachedLyrics(String songKey, String expectedRawLyricFile) throws IOException {
        if (!Files.isRegularFile(songCache)) {
            AmllLogger.verbose("CACHE", "Song cache does not exist.");
            return null;
        }

        for (String line : Files.readAllLines(songCache, StandardCharsets.UTF_8)) {
            int firstTab = line.indexOf('\t');
            if (firstTab < 0 || !line.substring(0, firstTab).equals(songKey)) continue;
            String[] fields = line.split("\t");
            if (fields.length < 5) return null;
            if (!CACHE_VERSION.equals(fields[4])) return null;
            if (expectedRawLyricFile != null && !expectedRawLyricFile.equals(fields[1])) return null;

            Path lyricPath = lyricsCache.resolve(fields[2]).normalize();
            if (!lyricPath.startsWith(lyricsCache) || !Files.isRegularFile(lyricPath)) return null;
            AmllLogger.info("CACHE", "Converted lyric cache hit.");
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
        clearMiss(songKey);
        AmllLogger.info("CACHE", "Converted lyric cache written for " + rawLyricFile + ".");
    }

    private String fetchText(String url) throws IOException {
        AmllLogger.verbose("SEARCH", "Requesting remote text: " + url + ".");
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "salt-player-amll-ttml-loader/1.0.1");

        int status = connection.getResponseCode();
        AmllLogger.info("SEARCH", "Remote request completed with HTTP " + status + ".");
        if (status < 200 || status > 299) {
            throw new IOException("HTTP " + status + " for " + url);
        }
        try (InputStream stream = connection.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private MatchScore score(PlaybackExtensionPoint.MediaItem mediaItem, IndexEntry entry) {
        String title = normalizeTitle(mediaItem.getTitle());
        String album = normalize(mediaItem.getAlbum());
        List<String> artists = splitArtists(String.join("/", nullToBlank(mediaItem.getArtist()), nullToBlank(mediaItem.getAlbumArtist())));

        List<String> entryTitles = entry.metadata.getOrDefault("musicName", List.of()).stream().map(AmllTtmlLoader::normalizeTitle).toList();
        List<String> entryAlbums = entry.metadata.getOrDefault("album", List.of()).stream().map(AmllTtmlLoader::normalize).toList();
        List<String> entryArtists = entry.metadata.getOrDefault("artists", List.of()).stream().flatMap(value -> splitArtists(value).stream()).toList();

        double titleSimilarity = bestTitleSimilarity(title, entryTitles);
        if (titleSimilarity < 0.72) return new MatchScore(0, titleSimilarity);

        int score = titleSimilarity >= 0.995 ? 150 : (int) Math.round(150 * titleSimilarity);
        if (!artists.isEmpty() && entryArtists.stream().anyMatch(entryArtist -> artists.stream().anyMatch(entryArtist::equals))) score += 45;
        else if (!artists.isEmpty() && entryArtists.stream().anyMatch(entryArtist -> artists.stream().anyMatch(artist -> artist.contains(entryArtist) || entryArtist.contains(artist)))) score += 20;
        if (!album.isBlank() && entryAlbums.stream().anyMatch(album::equals)) score += 25;
        else if (!album.isBlank() && entryAlbums.stream().anyMatch(value -> value.contains(album) || album.contains(value))) score += 10;
        return new MatchScore(score, titleSimilarity);
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
        return String.join("|", normalizeTitle(item.getTitle()), normalize(item.getArtist()), normalize(item.getAlbum()));
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

    private static String normalizeTitle(String value) {
        if (value == null) return "";
        String stripped = value.toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)|\\uFF08[^\\uFF09]*\\uFF09|\\[[^]]*]", " ")
                .replaceAll("(?i)\\b(tv\\s*size|short\\s*ver(?:sion)?|full\\s*ver(?:sion)?|instrumental|inst\\.?|off\\s*vocal|karaoke|live|remix|remaster(?:ed)?|cover|opening|ending|op|ed)\\b", " ")
                .replace("伴奏", " ")
                .replace("纯音乐", " ")
                .replace("翻唱", " ");
        return normalize(stripped);
    }

    private static double bestTitleSimilarity(String title, List<String> entryTitles) {
        if (title.isBlank() || entryTitles.isEmpty()) return 0;
        double best = 0;
        for (String entryTitle : entryTitles) {
            best = Math.max(best, titleSimilarity(title, entryTitle));
        }
        return best;
    }

    private static double titleSimilarity(String left, String right) {
        if (left.isBlank() || right.isBlank()) return 0;
        if (left.equals(right)) return 1;

        int minLength = Math.min(left.length(), right.length());
        int maxLength = Math.max(left.length(), right.length());
        if (left.contains(right) || right.contains(left)) {
            double lengthRatio = (double) minLength / maxLength;
            if (lengthRatio >= 0.70) return 0.92;
            if (lengthRatio >= 0.50) return 0.80;
            return 0.66;
        }

        double editSimilarity = 1.0 - (double) levenshteinDistance(left, right) / maxLength;
        return Math.max(editSimilarity, diceCoefficient(left, right));
    }

    private static double diceCoefficient(String left, String right) {
        if (left.length() < 2 || right.length() < 2) return 0;
        Set<String> leftPairs = bigrams(left);
        Set<String> rightPairs = bigrams(right);
        int intersection = 0;
        for (String pair : leftPairs) {
            if (rightPairs.contains(pair)) intersection++;
        }
        return (2.0 * intersection) / (leftPairs.size() + rightPairs.size());
    }

    private static Set<String> bigrams(String value) {
        Set<String> pairs = new HashSet<>();
        for (int index = 0; index < value.length() - 1; index++) {
            pairs.add(value.substring(index, index + 2));
        }
        return pairs;
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int column = 0; column <= right.length(); column++) previous[column] = column;
        for (int row = 1; row <= left.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int cost = left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(
                        Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
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

    private record MatchScore(int score, double titleSimilarity) {
    }

    private record ScoredEntry(IndexEntry entry, int score, double titleSimilarity) {
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "amll-ttml-online-search");
            thread.setDaemon(true);
            return thread;
        }
    }

    record SearchResult(String rawLyricFile, String title, String artist, String album, int score) {
        @Override
        public String toString() {
            return title + " - " + artist + " [" + album + "]  #" + score;
        }
    }
}
