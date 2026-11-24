package at.dumphey.itemeditor.engine;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reading and writing the one-file-per-definition YAML used by consumables and custom items. */
final class DefinitionFiles {

    private DefinitionFiles() {
    }

    static Map<String, Object> read(File file) {
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final Map<String, Object> raw = new LinkedHashMap<>();
        for (String k : yaml.getKeys(false)) {
            raw.put(k, Cfg.plain(yaml.get(k)));
        }
        return raw;
    }

    /** Written to a temporary file and moved into place, so a crash cannot leave a half-written file. */
    static void write(File target, Map<String, Object> raw) throws IOException {
        final File folder = target.getParentFile();
        if (folder != null && !folder.exists()) {
            folder.mkdirs();
        }
        final YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            yaml.set(e.getKey(), e.getValue());
        }
        final File temp = new File(folder, target.getName() + ".tmp");
        yaml.save(temp);
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A deep copy made of plain maps and lists, safe to modify. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> copy(Map<String, Object> raw) {
        return (Map<String, Object>) Cfg.plain(raw);
    }

    static String encode(ItemStack item) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    static ItemStack decode(String data) throws IOException, ClassNotFoundException {
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(
                new ByteArrayInputStream(Base64.getDecoder().decode(data.replaceAll("\\s", ""))))) {
            return (ItemStack) in.readObject();
        }
    }
}
