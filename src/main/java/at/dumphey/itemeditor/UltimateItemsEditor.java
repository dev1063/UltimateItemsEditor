package at.dumphey.itemeditor;

import at.dumphey.itemeditor.commands.UltimateItemsEditorCommand;
import at.dumphey.itemeditor.compatibility.VersionUtils;
import at.dumphey.itemeditor.events.InventoryClickListener;
import at.dumphey.itemeditor.events.PlayerChatListener;
import at.dumphey.itemeditor.events.PlayerQuitListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

public final class UltimateItemsEditor extends JavaPlugin {

    /** Name of the data folder (and plugin) before the rename to UltimateItemsEditor. */
    private static final String LEGACY_NAME = "ItemEditor";

    private static UltimateItemsEditor instance;

    public static UltimateItemsEditor getInstance() {
        return instance;
    }

    public UltimateItemsEditor() {
        instance = this;
    }

    @Override
    public void onLoad() {
        VersionUtils.ensureCorrectMinecraftVersion();
    }

    @Override
    public void onEnable() {
        migrateLegacyDataFolder();
        warnIfLegacyPluginIsPresent();

        // creates config.yml / palettes.yml / messages and loads templates, language and styles
        at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getStyleService().load();
        // reads rules/*.yml; registers a listener only for the triggers those rules use
        at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getTriggerEngine().load();

        registerCommands();
        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerChatListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(), this);

        at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getItemStorage().reload();

        if (isPlaceholderApiEnabled()) {
            getLogger().info("Enabled PlaceholderAPI support.");
        }
    }

    public boolean isPlaceholderApiEnabled() {
        return getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public void onDisable() {
        at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getTriggerEngine().shutdown();
        // writes are asynchronous: make sure every pending save/delete reaches the disk
        at.dumphey.itemeditor.itemeditor.ItemEditor.INSTANCE.getItemStorage().shutdown();
    }

    private void registerCommands() {
        final UltimateItemsEditorCommand cmd = new UltimateItemsEditorCommand();
        getCommand("ultimateitemseditor").setExecutor(cmd);
        getCommand("ultimateitemseditor").setTabCompleter(cmd);
    }

    /** plugins/ItemEditor (saved items, nothing else) becomes plugins/UltimateItemsEditor on the first start. */
    private void migrateLegacyDataFolder() {
        final File current = getDataFolder();
        final File legacy = new File(current.getParentFile(), LEGACY_NAME);
        if (!legacy.isDirectory() || legacy.equals(current)) {
            return;
        }
        if (current.exists()) {
            final String[] children = current.list();
            if (children == null || children.length > 0) {
                getLogger().warning("Found the old data folder plugins/" + LEGACY_NAME + " but plugins/" + current.getName()
                        + " already has files: nothing was migrated. Copy the 'items' folder over by hand if you need it.");
                return;
            }
            current.delete(); // an empty folder (created by the server before onEnable) must not block the migration
        }
        try {
            Files.move(legacy.toPath(), current.toPath());
            getLogger().info("Migrated plugins/" + LEGACY_NAME + " to plugins/" + current.getName() + ".");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not migrate plugins/" + LEGACY_NAME + " - move it to plugins/"
                    + current.getName() + " manually.", e);
        }
    }

    private void warnIfLegacyPluginIsPresent() {
        if (getServer().getPluginManager().isPluginEnabled(LEGACY_NAME)) {
            getLogger().severe("The old '" + LEGACY_NAME + "' plugin is still installed. Delete ItemEditor-*.jar from "
                    + "the plugins folder: both plugins use the /ie command and the same items.");
        }
    }
}
