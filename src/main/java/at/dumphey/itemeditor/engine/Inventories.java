package at.dumphey.itemeditor.engine;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Inventory helpers shared by the engine. */
final class Inventories {

    private Inventories() {
    }

    /**
     * Gives {@code amount} copies of an item. Whatever does not fit is dropped at the player's feet, in stacks that
     * respect the maximum stack size.
     *
     * @return how many items were dropped on the ground
     */
    static int give(Player player, ItemStack item, int amount) {
        final ItemStack toGive = item.clone();
        toGive.setAmount(amount);
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(toGive);
        int dropped = 0;
        for (ItemStack rest : leftover.values()) {
            int remaining = rest.getAmount();
            final int maxStack = Math.max(1, rest.getMaxStackSize());
            while (remaining > 0) {
                final ItemStack part = rest.clone();
                part.setAmount(Math.min(remaining, maxStack));
                player.getWorld().dropItemNaturally(player.getLocation(), part);
                remaining -= part.getAmount();
                dropped += part.getAmount();
            }
        }
        return dropped;
    }
}
