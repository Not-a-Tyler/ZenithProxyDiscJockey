package tyler.discjockey.utils;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static tyler.discjockey.DiscJockeyPlugin.LOG;

public class SongLoader {
    public static final ArrayList<Song> SONGS = new ArrayList<>();
    public static volatile boolean loadingSongs;

    public static void loadSongs() {
        if (loadingSongs) return;
        LOG.info("Starting song loading...");
        new Thread(() -> {
            loadingSongs = true;
            try {
                File songsDir = Paths.get("songs").toFile();
                if (!songsDir.exists()) {
                    if (!songsDir.mkdirs()) {
                        LOG.error("Failed to create songs directory: {}", songsDir.getAbsolutePath());
                        return;
                    }
                }

                File[] files = songsDir.listFiles(File::isFile);
                if (files == null) files = new File[0];

                if (files.length == 0) {
                    synchronized (SONGS) {
                        SONGS.clear();
                    }
                    LOG.info("Song loading complete, 0 songs found");
                    return;
                }

                int cpu = Runtime.getRuntime().availableProcessors();
                int poolSize = Math.min(8, Math.max(2, cpu));
                ExecutorService pool = Executors.newFixedThreadPool(poolSize);

                try {
                    ArrayList<Future<Song>> futures = new ArrayList<>(files.length);
                    for (File file : files) {
                        futures.add(pool.submit(new SongTask(file)));
                    }

                    ArrayList<Song> result = new ArrayList<>(files.length);
                    int completed = 0;
                    for (Future<Song> f : futures) {
                        try {
                            Song s = f.get();
                            if (s != null) result.add(s);
                        } catch (Exception e) {
                            // Error already logged inside task
                        }
                        completed++;
                        if (completed % 50 == 0 || completed == files.length) {
                            LOG.info("Loading progress: {}/{}", completed, files.length);
                        }
                    }

                    synchronized (SONGS) {
                        SONGS.clear();
                        SONGS.addAll(result);
                    }
                    LOG.info("Song loading complete, {} songs loaded", result.size());
                } finally {
                    pool.shutdown();
                }
            } finally {
                loadingSongs = false;
            }
        }, "song-loader").start();
    }

    private static final class SongTask implements Callable<Song> {
        private final File file;
        SongTask(File file) { this.file = file; }

        @Override
        public Song call() {
            try {
                Song s = loadSong(file);
                return s;
            } catch (Exception e) {
                LOG.error("Failed to load song {}: {}", file.getName(), e.getMessage(), e);
                return null;
            }
        }
    }

    public static Song loadSong(File file) throws IOException {
        if (!file.isFile()) return null;

        try (BinaryReader reader = new BinaryReader(Files.newInputStream(file.toPath()))) {
            Song song = new Song();

            song.fileName = stripCrLf(file.getName());

            song.length = reader.readShort();

            boolean newFormat = song.length == 0;
            if (newFormat) {
                song.formatVersion = reader.readByte();
                song.vanillaInstrumentCount = reader.readByte();
                song.length = reader.readShort();
            }

            song.height = reader.readShort();
            song.name = stripCrLf(reader.readString());
            song.author = stripCrLf(reader.readString());
            song.originalAuthor = stripCrLf(reader.readString());
            song.description = stripCrLf(reader.readString());
            song.tempo = reader.readShort();
            song.autoSaving = reader.readByte();
            song.autoSavingDuration = reader.readByte();
            song.timeSignature = reader.readByte();
            song.minutesSpent = reader.readInt();
            song.leftClicks = reader.readInt();
            song.rightClicks = reader.readInt();
            song.blocksAdded = reader.readInt();
            song.blocksRemoved = reader.readInt();
            song.importFileName = stripCrLf(reader.readString());

            if (newFormat) {
                song.loop = reader.readByte();
                song.maxLoopCount = reader.readByte();
                song.loopStartTick = reader.readShort();
            }

            // Display/search fields without regex overhead
            boolean nameEmpty = removeAllWhitespace(song.name).isEmpty();
            song.displayName = nameEmpty ? song.fileName : song.name + " (" + song.fileName + ")";
            song.searchableFileName = removeAllWhitespace(song.fileName.toLowerCase(Locale.ROOT));
            song.searchableName = removeAllWhitespace(song.name.toLowerCase(Locale.ROOT));

            // Use a local HashSet to avoid O(n^2) on uniqueNotes.contains
            Set<Note> seenUnique = new HashSet<>(64);

            // Efficient long collector for notes
            LongAppender notes = new LongAppender(1024);

            short tick = -1;
            short jumps;
            while ((jumps = reader.readShort()) != 0) {
                tick += jumps;
                short layer = -1;
                while ((jumps = reader.readShort()) != 0) {
                    layer += jumps;

                    byte instrumentId = reader.readByte();
                    byte noteId = (byte) (reader.readByte() - 33);

                    if (newFormat) {
                        reader.readByte();   // Velocity (unused)
                        reader.readByte();   // Panning (unused)
                        reader.readShort();  // Pitch (unused)
                    }

                    if (noteId < 0) noteId = 0;
                    else if (noteId > 24) noteId = 24;

                    Note note = new Note(Note.INSTRUMENTS[instrumentId], noteId);
                    if (seenUnique.add(note)) {
                        // Only add to song.uniqueNotes if not already present in our set
                        if (!song.uniqueNotes.contains(note)) {
                            song.uniqueNotes.add(note);
                        }
                    }

                    long packed = (tick & 0xFFFFL)
                            | ((long) layer << Note.LAYER_SHIFT)
                            | ((long) instrumentId << Note.INSTRUMENT_SHIFT)
                            | ((long) noteId << Note.NOTE_SHIFT);
                    notes.add(packed);
                }
            }

            song.notes = notes.toArray();
            return song;
        }
    }

    // Buffered, bulk-read BinaryReader
    public static class BinaryReader implements Closeable {
        private final InputStream in;
        private final ByteBuffer bufLE = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        private final byte[] tmp4 = new byte[4];

        public BinaryReader(InputStream in) {
            this.in = new BufferedInputStream(in, 64 * 1024);
        }

        private void readFully(byte[] b, int off, int len) throws IOException {
            int n = 0;
            while (n < len) {
                int r = in.read(b, off + n, len - n);
                if (r < 0) throw new EOFException();
                n += r;
            }
        }

        public int readInt() throws IOException {
            readFully(tmp4, 0, 4);
            return bufLE.clear().put(tmp4, 0, 4).rewind().getInt();
        }

        public short readShort() throws IOException {
            readFully(tmp4, 0, 2);
            return bufLE.clear().put(tmp4, 0, 2).rewind().getShort();
        }

        public String readString() throws IOException {
            int len = readInt();
            if (len <= 0) return "";
            byte[] b = new byte[len];
            readFully(b, 0, len);
            return new String(b, StandardCharsets.UTF_8);
        }

        public byte readByte() throws IOException {
            int b = in.read();
            if (b < 0) throw new EOFException();
            return (byte) b;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    // Efficient dynamic long array
    static final class LongAppender {
        private long[] a;
        private int size;

        LongAppender(int cap) {
            a = new long[Math.max(16, cap)];
        }

        void add(long v) {
            if (size == a.length) {
                int newCap = a.length + (a.length >>> 1) + 1;
                long[] n = new long[newCap];
                System.arraycopy(a, 0, n, 0, size);
                a = n;
            }
            a[size++] = v;
        }

        long[] toArray() {
            long[] out = new long[size];
            System.arraycopy(a, 0, out, 0, size);
            return out;
        }
    }

    private static String stripCrLf(String s) {
        if (s == null || s.isEmpty()) return s;
        // Remove only CR and LF to preserve other characters
        int len = s.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (c != '\n' && c != '\r') sb.append(c);
        }
        return sb.toString();
    }

    private static String removeAllWhitespace(String s) {
        if (s == null || s.isEmpty()) return s == null ? "" : s;
        int len = s.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            if (!Character.isWhitespace(c)) sb.append(c);
        }
        return sb.toString();
    }
}