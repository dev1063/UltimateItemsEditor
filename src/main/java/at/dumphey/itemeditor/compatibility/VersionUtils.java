package at.dumphey.itemeditor.compatibility;

import at.dumphey.itemeditor.UltimateItemsEditor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.inventory.meta.ItemMeta;

public class VersionUtils {
    private static final boolean MODERN_ITEM_API = detectModernItemApi();

    public static boolean hasModernItemApi() {
        return MODERN_ITEM_API;
    }

    private static boolean detectModernItemApi() {
        try {
            Class.forName("org.bukkit.inventory.view.AnvilView", false, ItemMeta.class.getClassLoader());
            Attribute.class.getField("MAX_HEALTH");
            ItemMeta.class.getMethod("setEnchantmentGlintOverride", Boolean.class);
            ItemMeta.class.getMethod("getConsumable");
            org.bukkit.Registry.class.getField("ATTRIBUTE");
            return true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            return false;
        }
    }

    public static void ensureCorrectMinecraftVersion() {
        UltimateItemsEditor.getInstance().getLogger().info("Bukkit " + Bukkit.getBukkitVersion()
                + (MODERN_ITEM_API ? " | modern item API" : " | base item API"));
    }

    public static Attribute maxHealthAttribute() {
        return HealthAttribute.VALUE;
    }

    private static final class HealthAttribute {
        private static final Attribute VALUE = find();

        private static Attribute find() {
            for (String name : new String[]{"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
                try {
                    return (Attribute) Attribute.class.getField(name).get(null);
                } catch (ReflectiveOperationException ignored) {
                    // Bukkit renamed the field when attributes became registry-backed.
                }
            }
            return null;
        }
    }
}
