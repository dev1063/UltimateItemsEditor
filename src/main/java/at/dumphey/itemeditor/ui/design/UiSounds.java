package at.dumphey.itemeditor.ui.design;

import at.dumphey.itemeditor.UltimateItemsEditor;
import org.bukkit.entity.Player;

/** The soft click every menu item plays. Can be switched off with gui.click-sounds in config.yml. */
public final class UiSounds {

    private UiSounds() {
    }

    public static void click(Player player) {
        final UltimateItemsEditor plugin = UltimateItemsEditor.getInstance();
        if (plugin == null || !plugin.getConfig().getBoolean("gui.click-sounds", true)) {
            return;
        }
        player.playSound(player.getLocation(), "ui.button.click", 0.4f, 1.3f);
    }
}
