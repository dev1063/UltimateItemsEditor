package at.dumphey.itemeditor.itemeditor.storage;

import org.bukkit.inventory.ItemStack;

public class SerializedItem {

    private final String name;
    private final ItemStack itemStack;
    private final String category;
    /** How the item is drawn in the list GUI; built on first use (a new SerializedItem replaces it on every save). */
    private ItemStack listIcon;

    public SerializedItem(String name, ItemStack itemStack) {
        this(name, itemStack, null);
    }

    public SerializedItem(String name, ItemStack itemStack, String category) {
        this.name = name;
        this.itemStack = itemStack;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
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
