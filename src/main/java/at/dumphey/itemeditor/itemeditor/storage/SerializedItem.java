package at.dumphey.itemeditor.itemeditor.storage;

import org.bukkit.inventory.ItemStack;

public class SerializedItem {

    private final String name;
    private final ItemStack itemStack;
    /** How the item is drawn in the list GUI; built on first use (a new SerializedItem replaces it on every save). */
    private ItemStack listIcon;

    public SerializedItem(String name, ItemStack itemStack) {
        this.name = name;
        this.itemStack = itemStack;
    }

    public String getName() {
        return name;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public ItemStack getListIcon() {
        return listIcon;
    }

    public void setListIcon(ItemStack listIcon) {
        this.listIcon = listIcon;
    }
}
