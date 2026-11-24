package at.dumphey.itemeditor;

import org.bukkit.configuration.Configuration;

import java.util.Locale;

/** Startup switches. Changes take effect at the next server restart. */
public final class PluginModules {
    public static final String VANILLA = "vanilla";
    public static final String CUSTOM_COMMANDS = "custom-commands";
    public static final String CUSTOM_ENCHANTMENTS = "custom-enchantments";

    private final boolean vanilla;
    private final boolean customCommands;
    private final boolean customEnchantments;

    public PluginModules(Configuration config) {
        vanilla = config.getBoolean("modules." + VANILLA, true);
        customCommands = config.getBoolean("modules." + CUSTOM_COMMANDS, true);
        customEnchantments = config.getBoolean("modules." + CUSTOM_ENCHANTMENTS, false);
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

    public boolean itemEditor() {
        return vanilla || customCommands || customEnchantments;
    }

    public boolean triggerEngine() {
        return customCommands || customEnchantments;
    }

    /** Config key of the disabled module that owns a command, or null if available. */
    public String disabledCommand(String command, String subcommand) {
        switch (command.toLowerCase(Locale.ROOT)) {
            case "template":
                return vanilla ? null : VANILLA;
            case "edit":
            case "reopen":
                return itemEditor() ? null : VANILLA + ", " + CUSTOM_COMMANDS + ", " + CUSTOM_ENCHANTMENTS;
            case "consumable":
            case "customitem":
            case "citem":
                return customCommands ? null : CUSTOM_COMMANDS;
            case "rules":
                return triggerEngine() ? null : CUSTOM_COMMANDS + ", " + CUSTOM_ENCHANTMENTS;
            case "tag":
                if ("enchant".equalsIgnoreCase(subcommand)) {
                    return customEnchantments ? null : CUSTOM_ENCHANTMENTS;
                }
                if ("item".equalsIgnoreCase(subcommand)) {
                    return customCommands ? null : CUSTOM_COMMANDS;
                }
                return triggerEngine() ? null : CUSTOM_COMMANDS + ", " + CUSTOM_ENCHANTMENTS;
            default:
                return null;
        }
    }
}
