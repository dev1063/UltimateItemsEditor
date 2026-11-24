package at.dumphey.itemeditor.ui;

import at.dumphey.itemeditor.UltimateItemsEditor;
import at.dumphey.itemeditor.ui.template.UiScreen;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

public class UiManager {

    private final Map<Player, UiScreen> screens = new HashMap<>();
    private Player clickingPlayer;

    public void onQuit(Player player) {
        screens.remove(player);
    }

    public void onInventoryClick(InventoryClickEvent event) {
        // This runs for every inventory click of every player on the server, so bail out first and cheaply:
        // only a player with one of our menus can ever matter (no title string is built, nothing is allocated).
        if (screens.isEmpty()) {
            return;
        }
        final HumanEntity who = event.getWhoClicked();
        if (!(who instanceof Player)) {
            return;
        }
        final UiScreen screen = screens.get(who);
        if (screen == null || !screen.isInventory(event.getInventory())) {
            return;
        }

        // can't ever steal from this
        event.setCancelled(true);

        // event.getSlot() is relative to the clicked inventory, so a click in the player's own inventory
        // must never be mapped onto a GUI slot.
        if (!screen.isInventory(event.getClickedInventory())) {
            return;
        }
        final Player previousClickingPlayer = clickingPlayer;
        clickingPlayer = (Player) who;
        try {
            screen.onClick(event.getSlot(), event.getClick());
        } finally {
            clickingPlayer = previousClickingPlayer;
        }
    }

    public void update(Player player) {
        final UiScreen screen = getScreen(player);
        if (screen == null) {
            return;
        }

        screen.update();
    }

    public UiScreen getScreen(Player player) {
        return screens.get(player);
    }


    public void open(Player player, UiScreen screen) {
        if (player == clickingPlayer) {
            final UiScreen current = screens.get(player);
            Bukkit.getScheduler().runTask(UltimateItemsEditor.getInstance(), () -> {
                if (player.isOnline() && screens.get(player) == current
                        && current.isInventory(player.getOpenInventory().getTopInventory())) {
                    open(player, screen);
                }
            });
            return;
        }
        screen.update();
        screen.show();
        screens.put(player, screen);
    }

    public void close(Player player) {
        final UiScreen screen = getScreen(player);
        if (screen == null) {
            return;
        }

        screen.hide();
        screens.remove(player);
    }

    public void reopen(Player player) {
        final UiScreen screen = getScreen(player);
        if (screen == null) {
            return;
        }
        screen.show();
    }

}
