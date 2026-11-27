package at.dumphey.itemeditor.itemeditor.storage;

import com.google.common.hash.Hashing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/** Disk format and replacement protocol for the saved item collection. */
final class ItemFiles {

    private static final String HEADER = "UIE1 ";
    private static final int HEADER_SIZE = HEADER.length() + 64 + 1;
    static final int MAX_PAYLOAD = 8 * 1024 * 1024;
    private static final byte[] DELETION_MARKER = "UIE deleted 1\n".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern ITEM_FILE = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern TEMP_FILE = Pattern.compile("[0-9a-f]{64}(?:\\.bak|\\.deleted)?(?:-[0-9]+)?\\.tmp");

    private final Path directory;
    private final Logger logger;
    private boolean directorySyncWarning;

    ItemFiles(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    void openDirectory() throws IOException {
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a real item storage directory: " + directory);
        }
    }

    static boolean isItemFile(String filename) {
        return ITEM_FILE.matcher(filename).matches();
    }

    Path primary(String key) {
        return directory.resolve(checksum(key.getBytes(StandardCharsets.UTF_8)));
    }

    static Path backup(Path primary) {
        return primary.resolveSibling(primary.getFileName() + ".bak");
    }

    static Path tombstone(Path primary) {
        return primary.resolveSibling(primary.getFileName() + ".deleted");
    }

    static String basename(String filename) {
        final int suffix = filename.indexOf('.');
        if (suffix < 0) return filename;
        if (filename.endsWith(".bak") || filename.endsWith(".deleted")) return filename.substring(0, suffix);
        return filename;
    }

    static boolean exists(Path file) {
        return !Files.notExists(file, LinkOption.NOFOLLOW_LINKS);
    }

    static boolean isDeleted(Path primary) throws IOException {
        final Path marker = tombstone(primary);
        if (!exists(marker)) return false;
        if (!Arrays.equals(DELETION_MARKER, read(marker))) {
            throw new IOException("Invalid saved-item deletion marker: " + marker);
        }
        return true;
    }

    void removeTemporaryFile(Path file) {
        if (!TEMP_FILE.matcher(file.getFileName().toString()).matches()
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not remove temporary saved item " + file, ex);
        }
    }

    static byte[] read(Path file) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Expected a regular item file: " + file);
        }
        try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
             ByteArrayOutputStream data = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (data.size() + count > MAX_PAYLOAD + HEADER_SIZE) {
                    throw new IOException("Item file exceeds the size limit");
                }
                data.write(buffer, 0, count);
            }
            return data.toByteArray();
        }
    }

    static String payload(byte[] data) throws IOException {
        final String text = new String(data, StandardCharsets.US_ASCII);
        if (!text.startsWith(HEADER)) {
            // Legacy files held only the Bukkit Base64 payload, with optional line breaks.
            return text.replace("\n", "").replace("\r", "").trim();
        }
        if (text.length() <= HEADER_SIZE || text.charAt(HEADER_SIZE - 1) != '\n') {
            throw new IOException("Invalid item file header");
        }
        final String hash = text.substring(HEADER.length(), HEADER_SIZE - 1);
        if (!hash.equals(checksum(data, HEADER_SIZE, data.length - HEADER_SIZE))) {
            throw new IOException("Item checksum mismatch");
        }
        return text.substring(HEADER_SIZE);
    }

    static byte[] encode(String payload) throws IOException {
        if (payload.length() > MAX_PAYLOAD) throw new IOException("Item exceeds the size limit");
        final byte[] body = payload.getBytes(StandardCharsets.US_ASCII);
        final byte[] header = (HEADER + checksum(body) + "\n").getBytes(StandardCharsets.US_ASCII);
        final byte[] file = Arrays.copyOf(header, HEADER_SIZE + body.length);
        System.arraycopy(body, 0, file, HEADER_SIZE, body.length);
        return file;
    }

    void save(Path file, byte[] data, boolean preservePrimary) throws IOException {
        openDirectory();
        checkRegularOrMissing(file);
        final Path marker = tombstone(file);
        if (isDeleted(file)) removeDeletedCopies(file);
        if (preservePrimary && exists(file)) {
            final byte[] saved = read(file);
            // Validate the checksum before replacing the known-good backup.
            payload(saved);
            replace(backup(file), saved);
        }
        replace(file, data);
        if (Files.deleteIfExists(marker)) syncDirectory();
    }

    void restore(Path file, byte[] data) throws IOException {
        checkRegularOrMissing(file);
        replace(file, data);
    }

    void delete(Path file) throws IOException {
        openDirectory();
        checkRegularOrMissing(file);
        checkRegularOrMissing(backup(file));
        replace(tombstone(file), DELETION_MARKER);
        cleanDeleted(file);
    }

    void cleanDeleted(Path file) {
        try {
            removeDeletedCopies(file);
            if (syncDirectory() && Files.deleteIfExists(tombstone(file))) syncDirectory();
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Could not clean up deleted item " + file, ex);
        }
    }

    private void removeDeletedCopies(Path file) throws IOException {
        final Path backup = backup(file);
        if (!isDeleted(file)) throw new IOException("Missing saved-item deletion marker");
        checkRegularOrMissing(file);
        checkRegularOrMissing(backup);
        Files.deleteIfExists(backup);
        Files.deleteIfExists(file);
    }

    private void replace(Path destination, byte[] data) throws IOException {
        checkRegularOrMissing(destination);
        final Path temp = Files.createTempFile(directory, destination.getFileName() + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                final ByteBuffer bytes = ByteBuffer.wrap(data);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            syncDirectory();
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "Could not remove temporary saved item " + temp, ex);
            }
        }
    }

    private boolean syncDirectory() {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
            return true;
        } catch (IOException | UnsupportedOperationException ex) {
            if (!directorySyncWarning) {
                directorySyncWarning = true;
                logger.log(Level.WARNING, "The item storage filesystem did not allow syncing directory changes", ex);
            }
            return false;
        }
    }

    private static void checkRegularOrMissing(Path file) throws IOException {
        if (exists(file) && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Refusing to modify an item path that is not a regular file: " + file);
        }
    }

    private static String checksum(byte[] data) {
        return checksum(data, 0, data.length);
    }

    private static String checksum(byte[] data, int offset, int length) {
        return Hashing.sha256().hashBytes(data, offset, length).toString();
    }
}
