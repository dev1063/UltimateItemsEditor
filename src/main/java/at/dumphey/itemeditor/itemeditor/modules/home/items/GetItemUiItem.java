package at.dumphey.itemeditor.itemeditor.modules.home.items;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

public class GetItemUiItem extends UiItem {


    public GetItemUiItem(UiScreen screen, Player player) {
        super(screen, player);
    }

    @Override
    public ItemStack onRender() {
        final ItemStack item = ItemEditor.INSTANCE.getItem(player);
        if (!ItemEditor.INSTANCE.isEditingSaved(player)) {
            return item;
        }

        // Editing a saved item: show where the click leads (on a copy, so the real lore stays untouched).
        return ItemBuilder.of(item.clone())
                          .withAppendLore("", "§8§l* §e" + ItemEditor.INSTANCE.getEditingSavedName(player),
                                  "  §7Left-click to §osave changes")
                          .build();
    }

    @Override
    public void onClick(ClickType clickType) {
        if (!clickType.isLeftClick()) {
            return;
        }

        ItemEditor.INSTANCE.finish(player);
    }
}
