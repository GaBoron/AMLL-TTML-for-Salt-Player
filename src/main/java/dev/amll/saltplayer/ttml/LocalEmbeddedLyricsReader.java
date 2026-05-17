package dev.amll.saltplayer.ttml;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class LocalEmbeddedLyricsReader {
    private static final int FLAC_VORBIS_COMMENT_BLOCK = 4;
    private static final int MAX_COMMENT_COUNT = 10_000;
    private static final List<String> LYRIC_FIELD_PRIORITY = List.of(
            "LYRICS",
            "SYNCEDLYRICS",
            "SYNCLYRICS",
            "UNSYNCEDLYRICS",
            "UNSYNCHEDLYRICS",
            "UNSYNCHRONIZEDLYRICS"
    );
    private static final List<String> TTML_FIELD_PRIORITY = List.of(
            "LYRICS",
            "SYNCEDLYRICS",
            "SYNCLYRICS",
            "UNSYNCEDLYRICS",
            "UNSYNCHEDLYRICS",
            "UNSYNCHRONIZEDLYRICS",
            "DESCRIPTION"
    );

    private LocalEmbeddedLyricsReader() {
    }

    static Optional<EmbeddedLyrics> read(Path audioPath) throws IOException {
        return readFlacVorbisComments(audioPath);
    }

    private static Optional<EmbeddedLyrics> readFlacVorbisComments(Path audioPath) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(audioPath, StandardOpenOption.READ)) {
            ByteBuffer header = ByteBuffer.allocate(4);
            if (!readFully(channel, header)) return Optional.empty();
            header.flip();
            if (header.get() != 'f' || header.get() != 'L' || header.get() != 'a' || header.get() != 'C') {
                return Optional.empty();
            }

            boolean lastBlock = false;
            while (!lastBlock) {
                header.clear();
                if (!readFully(channel, header)) return Optional.empty();
                header.flip();

                int first = header.get() & 0xff;
                lastBlock = (first & 0x80) != 0;
                int blockType = first & 0x7f;
                int length = ((header.get() & 0xff) << 16) | ((header.get() & 0xff) << 8) | (header.get() & 0xff);

                if (blockType != FLAC_VORBIS_COMMENT_BLOCK) {
                    channel.position(channel.position() + length);
                    continue;
                }

                ByteBuffer data = ByteBuffer.allocate(length);
                if (!readFully(channel, data)) return Optional.empty();
                return selectLyrics(parseVorbisComments(data.array()));
            }
        }
        return Optional.empty();
    }

    private static List<Comment> parseVorbisComments(byte[] data) {
        List<Comment> comments = new ArrayList<>();
        int offset = 0;
        Long vendorLength = readUInt32Le(data, offset);
        if (vendorLength == null || vendorLength > data.length - 4L) return comments;
        offset += 4 + vendorLength.intValue();

        Long commentCount = readUInt32Le(data, offset);
        if (commentCount == null || commentCount > MAX_COMMENT_COUNT) return comments;
        offset += 4;

        for (int index = 0; index < commentCount; index++) {
            Long length = readUInt32Le(data, offset);
            if (length == null || length > data.length - offset - 4L) break;
            offset += 4;

            String raw = new String(data, offset, length.intValue(), StandardCharsets.UTF_8);
            offset += length.intValue();

            int equals = raw.indexOf('=');
            if (equals <= 0) continue;

            String name = raw.substring(0, equals).trim().toUpperCase(Locale.ROOT);
            String value = stripBom(raw.substring(equals + 1)).trim();
            if (!value.isBlank()) comments.add(new Comment(name, value));
        }
        return comments;
    }

    private static Optional<EmbeddedLyrics> selectLyrics(List<Comment> comments) {
        for (String fieldName : TTML_FIELD_PRIORITY) {
            for (Comment comment : comments) {
                if (fieldName.equals(comment.name()) && looksLikeTtml(comment.value())) {
                    return Optional.of(new EmbeddedLyrics(comment.value(), comment.name(), true));
                }
            }
        }
        for (String fieldName : LYRIC_FIELD_PRIORITY) {
            for (Comment comment : comments) {
                if (fieldName.equals(comment.name())) {
                    return Optional.of(new EmbeddedLyrics(comment.value(), comment.name(), false));
                }
            }
        }
        return Optional.empty();
    }

    private static boolean looksLikeTtml(String lyrics) {
        String clean = stripBom(lyrics).trim();
        return clean.startsWith("<tt ")
                || clean.startsWith("<tt>")
                || clean.startsWith("<?xml") && clean.contains("<tt");
    }

    private static String stripBom(String value) {
        return value != null && value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static Long readUInt32Le(byte[] data, int offset) {
        if (offset < 0 || offset + 4 > data.length) return null;
        return (long) (data[offset] & 0xff)
                | (long) (data[offset + 1] & 0xff) << 8
                | (long) (data[offset + 2] & 0xff) << 16
                | (long) (data[offset + 3] & 0xff) << 24;
    }

    private static boolean readFully(SeekableByteChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) return false;
        }
        return true;
    }

    record EmbeddedLyrics(String lyrics, String fieldName, boolean ttml) {
    }

    private record Comment(String name, String value) {
    }
}
