package at.dumphey.itemeditor.compatibility;

import at.dumphey.itemeditor.UltimateItemsEditor;
import org.bukkit.Bukkit;

import java.util.logging.Level;

public class VersionUtils {


    public static void ensureCorrectMinecraftVersion() {
        try {
            String versionString = Bukkit.getServer().getVersion();
            String mc = versionString.substring(versionString.indexOf("MC: ") + 4);
            mc = mc.substring(2, mc.indexOf(')'));
            String[] parts = mc.split("\\.");
            int majorVersion = Integer.parseInt(parts[0]);
            int minorVersion;
            if (parts.length > 1) {
                minorVersion = Integer.parseInt(parts[1]);
            } else {
                minorVersion = 0;
            }

            String mcVersion = "1." + majorVersion + "." + minorVersion;

            UltimateItemsEditor.getInstance()
                    .getLogger()
                    .log(Level.INFO, "Detected Minecraft version " + mcVersion);

            if (majorVersion < 21) {
                // disable plugin and log a warning along with info on how to fix
                UltimateItemsEditor.getInstance()
                        .getLogger()
                        .log(Level.WARNING, "[!!!] UltimateItemsEditor is not compatible with this version of Minecraft. To use it with Minecraft " + mcVersion + ", please download a previous build from https://www.spigotmc.org/resources/item-editor-2.27637/.");

                UltimateItemsEditor.getInstance().getServer().getPluginManager().disablePlugin(UltimateItemsEditor.getInstance());
            }
        } catch (Exception e) {
            UltimateItemsEditor.getInstance().getLogger().log(Level.SEVERE, "Failed to parse version. UltimateItemsEditor might not work as expected.", e);
        }
    }
}
