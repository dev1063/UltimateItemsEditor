package at.dumphey.itemeditor.api;

import org.bukkit.inventory.ItemStack;

/** Optional Bukkit service for shops, crates and future integrations. Call on the server thread. */
public interface ConsumableRewards {
    /** Returns a fresh tagged reward, or null for a missing, disabled or incomplete consumable. */
    ItemStack createItem(String id);
}
