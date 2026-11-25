package at.dumphey.itemeditor;

import org.bukkit.configuration.Configuration;

import java.util.Locale;

/** Startup switches. Changes take effect at the next server restart. */
public final class PluginModules {
    public static final String VANILLA = "vanilla";
    public static final String CUSTOM_COMMANDS = "custom-commands";
    public static final String CONSUMABLES = "consumables";
    public static final String CUSTOM_ENCHANTMENTS = "custom-enchantments";

    private final boolean vanilla;
    private final boolean customCommands;
    private final boolean customEnchantments;
    private final boolean consumables;
    private final boolean modernApi;

    public PluginModules(Configuration config) {
        this(config, true);
    }

    public PluginModules(Configuration config, boolean modernApi) {
        this.modernApi = modernApi;
        vanilla = config.getBoolean("modules." + VANILLA, true);
        customCommands = config.getBoolean("modules." + CUSTOM_COMMANDS, true);
        consumables = config.isSet("modules." + CONSUMABLES)
                ? config.getBoolean("modules." + CONSUMABLES) : customCommands;
        customEnchantments = modernApi && config.getBoolean("modules." + CUSTOM_ENCHANTMENTS, false);
    }

    public boolean vanilla() {
        return vanilla;
    }

    public boolean customCommands() {
        return customCommands;
    }

    public boolean customEnchantments() {
        return customEnchantments;
    }

    public boolean consumables() {
        return consumables;
    }

    public boolean itemEditor() {
        return vanilla || customCommands || customEnchantments || consumables;
    }

    public boolean triggerEngine() {
        return customCommands || customEnchantments || consumables;
    }

    /** Config key of the disabled module that owns a command, or null if available. */
    public String disabledCommand(String command, String subcommand) {
        switch (command.toLowerCase(Locale.ROOT)) {
            case "template":
                return vanilla && modernApi ? null : VANILLA;
            case "edit":
            case "reopen":
                return null; // The editor can show disabled module pages.
            case "consumable":
                return consumables ? null : CONSUMABLES;
            case "customitem":
            case "citem":
                return customCommands ? null : CUSTOM_COMMANDS;
            case "rules":
                return triggerEngine() ? null : CUSTOM_COMMANDS + ", " + CUSTOM_ENCHANTMENTS + ", " + CONSUMABLES;
            case "tag":
                if ("enchant".equalsIgnoreCase(subcommand)) {
                    return customEnchantments ? null : CUSTOM_ENCHANTMENTS;
                }
                if ("item".equalsIgnoreCase(subcommand)) {
                    return customCommands ? null : CUSTOM_COMMANDS;
                }
                return customCommands || customEnchantments ? null : CUSTOM_COMMANDS + ", " + CUSTOM_ENCHANTMENTS;
            default:
                return null;
        }
    }
}
