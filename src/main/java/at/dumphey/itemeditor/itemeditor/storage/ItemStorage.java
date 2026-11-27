package at.dumphey.itemeditor.itemeditor.storage;

import at.dumphey.itemeditor.UltimateItemsEditor;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.ObjectInputFilter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Saved items. The items live in memory (that is what the GUI and commands read); every change is serialized on the
 * main thread (Bukkit objects must not leave it) and the file write/delete is queued to one background thread, so
 * saving never blocks the server. The thread exists only while there is something to write and ends 5 seconds after.
 * Files are written to a temporary file before replacing the previous copy.
 */
public class ItemStorage {

    private static final int QUEUE_CAPACITY = 256;
    private static final long MAX_QUEUED_BYTES = 64L * 1024 * 1024;
    private static final Comparator<SerializedItem> BY_NAME = new Comparator<SerializedItem>() {
        @Override
        public int compare(SerializedItem a, SerializedItem b) {
            return String.CASE_INSENSITIVE_ORDER.compare(a.getName(), b.getName());
        }
    };

    private final Map<String, SerializedItem> storedItems = new HashMap<>();
    private List<SerializedItem> sortedCache;
    private final Map<String, SerializedItem> durable = new ConcurrentHashMap<>();
    private final Map<String, Object> pending = new ConcurrentHashMap<>();
    private final Set<String> recovered = ConcurrentHashMap.newKeySet();
    private final AtomicLong queuedBytes = new AtomicLong();
    private final UltimateItemsEditor plugin = UltimateItemsEditor.getInstance();
    private final Path directory = Paths.get(plugin.getDataFolder().getPath(), "items");
    private final ItemFiles files = new ItemFiles(directory, plugin.getLogger());
    private boolean loaded;

    /** No thread until the first write, then one background writer in submission order. */
    private final ThreadPoolExecutor io = new ThreadPoolExecutor(0, 1, 5L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
        final Thread thread = new Thread(runnable, "UltimateItemsEditor-IO");
        thread.setDaemon(true);
        return thread;
    });

    public void reload() {
        plugin.getLogger().info("Reloading items.");
        if (!pending.isEmpty()) {
            try {
                io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | RejectedExecutionException e) {
                plugin.getLogger().log(Level.WARNING, "Could not flush items before reloading", e);
                return;
            }
        }
        final Map<String, SerializedItem> loadedItems = new HashMap<>();
        final Set<String> recoveredItems = new HashSet<>();
        try {
            files.openDirectory();
            final Set<String> names = new HashSet<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
                for (Path entry : entries) {
                    final String name = entry.getFileName().toString();
                    final String base = ItemFiles.basename(name);
                    if (ItemFiles.isItemFile(base)) names.add(base);
                    else files.removeTemporaryFile(entry);
                }
            }
            for (String filename : names) {
                loadItem(directory.resolve(filename), loadedItems, recoveredItems);
            }
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not read the saved item directory", ex);
            return;
        }
        pending.clear();
        recovered.clear();
        recovered.addAll(recoveredItems);
        storedItems.clear();
        storedItems.putAll(loadedItems);
        durable.clear();
        durable.putAll(loadedItems);
        sortedCache = null;
        loaded = true;
        plugin.getLogger().info("Loaded " + storedItems.size() + " items from storage.");
    }

    /** Waits (at most 10 seconds) for the queued writes during plugin shutdown. */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Some saved items could not be written in time.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void loadItem(Path primary, Map<String, SerializedItem> items, Set<String> recoveredItems) {
        try {
            if (ItemFiles.isDeleted(primary)) {
                files.cleanDeleted(primary);
                return;
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Ignoring invalid deletion marker for " + primary, ex);
        }
        Exception primaryError;
        try {
            final SerializedItem item = decode(primary, ItemFiles.read(primary));
            items.put(item.getName(), item);
            return;
        } catch (IOException | ClassNotFoundException | RuntimeException ex) {
            primaryError = ex;
        }
        try {
            final byte[] backup = ItemFiles.read(ItemFiles.backup(primary));
            final SerializedItem item = decode(primary, backup);
            items.put(item.getName(), item);
            try {
                files.restore(primary, backup);
            } catch (IOException ex) {
                recoveredItems.add(item.getName());
                plugin.getLogger().log(Level.WARNING, "Could not repair saved item " + primary, ex);
            }
            plugin.getLogger().warning("Loaded saved item '" + item.getName() + "' from its backup.");
        } catch (IOException | ClassNotFoundException | RuntimeException ex) {
            primaryError.addSuppressed(ex);
            plugin.getLogger().log(Level.SEVERE, "Could not load saved item " + primary, primaryError);
        }
    }

    private SerializedItem decode(Path primary, byte[] record) throws IOException, ClassNotFoundException {
        final SerializedItem item = deserializeItem(ItemFiles.payload(record));
        if (!files.primary(item.getName()).equals(primary)) {
            throw new IOException("Saved item name does not match its file");
        }
        return item;
    }

    public Executor mainThreadExecutor() {
        return task -> Bukkit.getScheduler().runTask(plugin, task);
    }

    public Collection<SerializedItem> getItems() {
        return Collections.unmodifiableCollection(storedItems.values());
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

    /** Rewrites the item while keeping its category, including categories removed from configuration. */
    public CompletableFuture<Void> saveItem(String name, ItemStack itemStack) throws IOException {
        final SerializedItem previous = getItem(name);
        return saveItem(name, itemStack, previous == null ? null : previous.getCategory());
    }

    /** Publishes a snapshot in memory. Completion confirms that its file was written. */
    public CompletableFuture<Void> saveItem(String name, ItemStack itemStack, String category) throws IOException {
        final String key = cleanName(name);
        if (key.isEmpty() || itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0) {
            throw new IOException("A saved entry needs a name and a usable item");
        }
        final SerializedItem item = new SerializedItem(key, itemStack.clone(), category);
        return change(key, item, ItemFiles.encode(serializeItem(item)));
    }

    public CompletableFuture<Boolean> deleteItem(String name) {
        final String key = cleanName(name);
        if (!storedItems.containsKey(key)) return CompletableFuture.completedFuture(false);
        try {
            return change(key, null, null).thenApply(result -> true);
        } catch (IOException ex) {
            final CompletableFuture<Boolean> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    private CompletableFuture<Void> change(String key, SerializedItem item, byte[] data) throws IOException {
        if (!loaded || io.isShutdown()) throw new IOException("Item storage is unavailable");
        final int bytes = data == null ? 0 : data.length;
        if (queuedBytes.addAndGet(bytes) > MAX_QUEUED_BYTES) {
            queuedBytes.addAndGet(-bytes);
            throw new IOException("The saved item queue is full");
        }
        final Object mutation = new Object();
        final Path file = files.primary(key);
        final CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (pending) {
            try {
                io.execute(() -> writeChange(key, item, data, file, mutation, result, bytes));
                pending.put(key, mutation);
                setItem(key, item);
            } catch (RejectedExecutionException ex) {
                queuedBytes.addAndGet(-bytes);
                throw new IOException("The saved item queue is unavailable or full", ex);
            }
        }
        return result;
    }

    private void writeChange(String key, SerializedItem item, byte[] data, Path file, Object mutation,
                             CompletableFuture<Void> result, int bytes) {
        try {
            final boolean alreadySaved = durable.containsKey(key);
            if (item == null) {
                files.delete(file);
                durable.remove(key);
            } else {
                if (!alreadySaved && !ItemFiles.isDeleted(file)
                        && (ItemFiles.exists(file) || ItemFiles.exists(ItemFiles.backup(file)))) {
                    throw new IOException("Existing files for this item could not be loaded. They were retained.");
                }
                files.save(file, data, alreadySaved && !recovered.contains(key));
                durable.put(key, item);
            }
            recovered.remove(key);
            synchronized (pending) {
                pending.remove(key, mutation);
            }
            result.complete(null);
        } catch (IOException | RuntimeException ex) {
            rollback(key, mutation, result, ex);
        } finally {
            queuedBytes.addAndGet(-bytes);
        }
    }

    private void rollback(String key, Object mutation, CompletableFuture<Void> result, Exception error) {
        plugin.getLogger().log(Level.SEVERE, "Could not update saved item '" + key + "'", error);
        if (plugin.isEnabled()) {
            try {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (pending.remove(key, mutation)) setItem(key, durable.get(key));
                    result.completeExceptionally(error);
                });
                return;
            } catch (IllegalPluginAccessException ignored) {
                // The plugin may stop between checking and scheduling.
            }
        }
        result.completeExceptionally(error);
    }

    private void setItem(String name, SerializedItem item) {
        if (item == null) storedItems.remove(name);
        else storedItems.put(name, item);
        sortedCache = null;
    }

    static String serializeItem(SerializedItem item) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream output = new BukkitObjectOutputStream(bytes)) {
            output.writeUTF(item.getName());
            output.writeObject(item.getItemStack());
            output.writeUTF(item.getCategory() == null ? "" : item.getCategory());
        }
        if (bytes.size() > ItemFiles.MAX_PAYLOAD / 4 * 3) throw new IOException("Item exceeds the size limit");
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static SerializedItem deserializeItem(String data) throws IOException, ClassNotFoundException {
        if (data.length() > ItemFiles.MAX_PAYLOAD) throw new IOException("Item exceeds the size limit");
        final byte[] bytes = Base64.getDecoder().decode(data);
        try (BukkitObjectInputStream input = new BukkitObjectInputStream(new ByteArrayInputStream(bytes))) {
            input.setObjectInputFilter(info -> info.depth() > 96 || info.references() > 100_000
                    || info.arrayLength() > ItemFiles.MAX_PAYLOAD || info.streamBytes() > ItemFiles.MAX_PAYLOAD
                    ? ObjectInputFilter.Status.REJECTED : ObjectInputFilter.Status.UNDECIDED);
            final String name = input.readUTF();
            final ItemStack item = (ItemStack) input.readObject();
            if (name.trim().isEmpty() || item == null) throw new IOException("Invalid saved item");
            String category = null;
            try {
                category = input.readUTF();
            } catch (EOFException ignored) {
                // The legacy format ended directly after the item.
            }
            return new SerializedItem(name, item, category == null || category.isEmpty() ? null : category);
        }
    }

    private String cleanName(String name) {
        return name.toLowerCase().trim();
    }
}
