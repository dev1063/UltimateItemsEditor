package at.dumphey.itemeditor;

import at.dumphey.itemeditor.api.ConsumableRewards;
import at.dumphey.itemeditor.commands.UltimateItemsEditorCommand;
import at.dumphey.itemeditor.compatibility.VersionUtils;
import at.dumphey.itemeditor.events.InventoryClickListener;
import at.dumphey.itemeditor.events.PlayerChatListener;
import at.dumphey.itemeditor.events.PlayerQuitListener;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.components.ArmorSets;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.ServicePriority;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;

public final class UltimateItemsEditor extends JavaPlugin {

    /** Name of the data folder (and plugin) before the rename to UltimateItemsEditor. */
    private static final String LEGACY_NAME = "ItemEditor";

    private static UltimateItemsEditor instance;
    private PluginModules modules;
    private ArmorSets armorSets;

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

        saveDefaultConfig();
        modules = new PluginModules(getConfig(), VersionUtils.hasModernItemApi());

        final ItemEditor editor = ItemEditor.INSTANCE;
        editor.getStyleService().load();
        if (modules.vanilla() && VersionUtils.hasModernItemApi()
                && getConfig().getBoolean("vanilla-armor-sets.enabled", false)) {
            armorSets = new ArmorSets(this);
            armorSets.start();
        }
        if (modules.triggerEngine()) {
            editor.getTriggerEngine().load();
        }

        if (modules.consumables()) {
            getServer().getServicesManager().register(ConsumableRewards.class,
                    editor.getTriggerEngine().consumables()::createRewardItem, this, ServicePriority.Normal);
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(new InventoryClickListener(), this);
        if (modules.itemEditor()) {
            getServer().getPluginManager().registerEvents(new PlayerChatListener(), this);
        }
        getServer().getPluginManager().registerEvents(new PlayerQuitListener(), this);
        editor.getItemStorage().reload();

        if (modules.vanilla() && isPlaceholderApiEnabled()) {
            getLogger().info("Enabled PlaceholderAPI support.");
        }
    }

    public ArmorSets armorSets() {
        return armorSets;
    }

    public PluginModules modules() {
        return modules;
    }

    public boolean isPlaceholderApiEnabled() {
        return getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    @Override
    public void onDisable() {
        if (modules == null) {
            return;
        }
        if (armorSets != null) {
            armorSets.stop();
            armorSets = null;
        }
        getServer().getServicesManager().unregisterAll(this);
        final ItemEditor editor = ItemEditor.INSTANCE;
        if (modules.triggerEngine()) {
            editor.getTriggerEngine().shutdown();
        }
        editor.getItemStorage().shutdown();
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
