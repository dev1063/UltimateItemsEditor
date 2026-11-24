package at.dumphey.itemeditor.itemeditor.storage;

import at.dumphey.itemeditor.UltimateItemsEditor;
import com.google.common.hash.Hashing;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Saved items. The items live in memory (that is what the GUI and commands read); every change is serialized on the
 * main thread (Bukkit objects must not leave it) and the file write/delete is queued to one background thread, so
 * saving never blocks the server. The thread exists only while there is something to write and ends 5 seconds after.
 * Files are written to a temporary file and moved into place, so a crash can never leave a half-written item.
 */
public class ItemStorage {

    private static final String TEMP_SUFFIX = ".tmp";
    private static final Comparator<SerializedItem> BY_NAME = new Comparator<SerializedItem>() {
        @Override
        public int compare(SerializedItem a, SerializedItem b) {
            return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
        }
    };

    private final Map<String, SerializedItem> storedItems = new HashMap<>();
    private List<SerializedItem> sortedCache;

    /** core 0 / max 1 / unbounded queue: no thread at all until the first write, one thread afterwards, FIFO order. */
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), runnable -> {
        final Thread thread = new Thread(runnable, "UltimateItemsEditor-IO");
        thread.setDaemon(true);
        return thread;
    });

    public void reload() {
        UltimateItemsEditor.getInstance().getLogger().log(Level.INFO, "Reloading items.");
        storedItems.clear();
        sortedCache = null;
        final File storageFolder = getStoragePath().toFile();
        if (!storageFolder.exists()) {
            storageFolder.mkdirs();
        }
        final File[] files = storageFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (!file.isFile()) {
                    continue;
                }
                if (file.getName().endsWith(TEMP_SUFFIX)) {
                    file.delete(); // leftover of a write that never finished; the real file is still intact
                    continue;
                }
                try {
                    loadItem(file);
                } catch (Exception e) { // make sure to catch any weird errors
                    UltimateItemsEditor.getInstance()
                            .getLogger()
                            .log(Level.SEVERE, "Error while loading item from file " + file.toPath(), e);
                }
            }
        }
        UltimateItemsEditor.getInstance()
                .getLogger()
                .log(Level.INFO, "Loaded " + storedItems.size() + " items from storage.");
    }

    /** Waits (at most 10 seconds) for the pending writes. Call from onDisable. */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                UltimateItemsEditor.getInstance().getLogger().warning("Some saved items could not be written in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Path getStoragePath() {
        return Paths.get(UltimateItemsEditor.getInstance().getDataFolder().getPath(), "items");
    }

    private void loadItem(File file) throws IOException, ClassNotFoundException {
        final String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        final SerializedItem item = deserializeItem(content.replace("\n", "").replace("\r", "").trim());
        storedItems.put(item.getName(), item);
    }

    public Collection<SerializedItem> getItems() {
        return storedItems.values();
    }

    /** All items ordered by name. The list is cached until the next change: do not modify it. */
    public List<SerializedItem> getSortedItems() {
        if (sortedCache == null) {
            final List<SerializedItem> sorted = new ArrayList<>(storedItems.values());
            Collections.sort(sorted, BY_NAME);
            sortedCache = Collections.unmodifiableList(sorted);
        }
        return sortedCache;
    }

    public int size() {
        return storedItems.size();
    }

    public SerializedItem getItem(String name) {
        return storedItems.get(cleanName(name));
    }

    /** Stores the item in memory right away and writes it to disk in the background. */
    public void saveItem(String name, ItemStack itemStack) throws IOException {
        name = cleanName(name);
        final SerializedItem item = new SerializedItem(name, itemStack);
        final String data = serializeItem(item); // main thread; fails here, before anything changed
        final Path file = fileFor(name);
        storedItems.put(name, item);
        sortedCache = null;
        io.execute(() -> {
            try {
                writeAtomically(file, data);
            } catch (IOException e) {
                UltimateItemsEditor.getInstance().getLogger()
                        .log(Level.SEVERE, "Could not write item file " + file.getFileName(), e);
            }
        });
    }

    public boolean deleteItem(String name) {
        name = cleanName(name);
        if (storedItems.remove(name) == null) {
            return false;
        }
        sortedCache = null;
        final Path file = fileFor(name);
        io.execute(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                UltimateItemsEditor.getInstance().getLogger()
                        .log(Level.SEVERE, "Could not delete item file " + file.getFileName(), e);
            }
        });
        return true;
    }

    private Path fileFor(String cleanName) {
        return Paths.get(getStoragePath().toString(), Hashing.sha256().hashString(cleanName, StandardCharsets.UTF_8).toString());
    }

    private static void writeAtomically(Path file, String data) throws IOException {
        final Path temp = file.resolveSibling(file.getFileName() + TEMP_SUFFIX);
        Files.write(temp, Collections.singleton(data));
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String serializeItem(SerializedItem item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput;
        dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeUTF(item.getName());
        dataOutput.writeObject(item.getItemStack());
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    private static String padBase64String(String base64String) {
        int padding = (4 - base64String.length() % 4) % 4;
        StringBuilder paddedBase64 = new StringBuilder(base64String);
        for (int i = 0; i < padding; i++) {
            paddedBase64.append("=");
        }
        return paddedBase64.toString();
    }

    private SerializedItem deserializeItem(String data) throws IOException, ClassNotFoundException {
        String paddedData = padBase64String(data);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(paddedData));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        String name = dataInput.readUTF();
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return new SerializedItem(name, item);
    }

    private String cleanName(String name) {
        return name.toLowerCase().trim();
    }
}
