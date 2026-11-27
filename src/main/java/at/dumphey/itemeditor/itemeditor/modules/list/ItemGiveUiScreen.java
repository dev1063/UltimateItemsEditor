package at.dumphey.itemeditor.itemeditor.modules.list;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.ui.UiPosition;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.FillerUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pick an amount and click an online player's head to give them the saved item.
 */
public class ItemGiveUiScreen extends UiScreen {

    /** Same upper limit as the /ie give command. */
    public static final int MAX_AMOUNT = 2304;

    private static final int PAGE_SIZE = 36;
    private static final int[] AMOUNT_PRESETS = {1, 2, 4, 8, 16, 32, 64};

    private final String itemName;
    private final String listCategory;
    private final int listPage;
    private int page = 0;
    private int amount = 1;

    public ItemGiveUiScreen(Player player, String itemName, String listCategory, int listPage) {
        super(player, 54, "Give item");
        this.itemName = itemName;
        this.listCategory = listCategory;
        this.listPage = listPage;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    private void setBack() {
        setItem(UiPosition.of(5, 0), new ActionUiItem(this, player,
                ItemBuilder.uiItem(Material.ARROW, "§cBack", "Back to the item"),
                () -> open(new ItemActionsUiScreen(player, itemName, listCategory, listPage))));
    }

    @Override
    protected void onUpdate() {
        final SerializedItem stored = ItemEditor.INSTANCE.getItemStorage().getItem(itemName);

        setBack();

        if (stored == null) {
            setItem(22, new StaticUiItem(this, player,
                    ItemBuilder.uiItem(Material.BARRIER, "Item not found", "It may have been deleted.")));
            fillEmptyWithFiller();
            return;
        }

        // Row 0: preview, amount presets, custom amount
        setItem(0, new StaticUiItem(this, player, stored.getItemStack().clone()));

        for (int i = 0; i < AMOUNT_PRESETS.length; i++) {
            final int preset = AMOUNT_PRESETS[i];
            final boolean selected = preset == amount;
            final ItemStack icon = ItemBuilder.uiItem(
                    selected ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    (selected ? "§a" : "§7") + preset + "x",
                    selected ? "Selected amount" : "Click to select");
            icon.setAmount(preset);
            setItem(1 + i, new ActionUiItem(this, player, icon, () -> {
                amount = preset;
                update();
            }));
        }

        setItem(8, new ActionUiItem(this, player,
                ItemBuilder.uiItem(Material.NAME_TAG, "Custom amount",
                        "Current: §f" + amount + "x\n§8Click and type an amount in chat"),
                () -> prompt(new SetGiveAmountPrompt(player, this))));

        // Rows 1-4: online players
        final List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        final int pageCount = Math.max(1, (players.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pageCount) {
            page = pageCount - 1;
        }

        int slot = 9;
        for (int i = page * PAGE_SIZE; i < players.size() && i < (page + 1) * PAGE_SIZE; i++) {
            final Player target = players.get(i);
            final ItemStack head = ItemBuilder.of(Material.PLAYER_HEAD)
                                              .withName("§e" + target.getName())
                                              .withLore("§8§l* §8Click to give §f" + amount + "x §e" + stored.getName())
                                              .editMeta(meta -> {
                                                  if (meta instanceof SkullMeta) {
                                                      ((SkullMeta) meta).setOwningPlayer(target);
                                                  }
                                              })
                                              .build();
            setItem(slot++, new ActionUiItem(this, player, head, () -> give(target)));
        }

        // Row 5: navigation
        final UiItem[] bar = PageBar.create(this, player, page, pageCount, players.size() + " player(s) online", newPage -> {
            page = newPage;
            update();
        });
        setItem(UiPosition.of(5, 3), bar[0]);
        setItem(UiPosition.of(5, 4), bar[1]);
        setItem(UiPosition.of(5, 5), bar[2]);

        fillEmpty(UiPosition.of(5, 0), UiPosition.of(5, 8), new FillerUiItem(this, player));
    }

    private void give(Player target) {
        final SerializedItem stored = ItemEditor.INSTANCE.getItemStorage().getItem(itemName);
        if (stored == null) {
            Messages.send(player, "§cThat item no longer exists.");
            update();
            return;
        }
        if (!target.isOnline()) {
            Messages.send(player, "§cThe player §o" + target.getName() + " §cis no longer online.");
            update();
            return;
        }

        final int dropped = ItemEditor.INSTANCE.giveItem(target, stored, amount);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
        Messages.send(player, "Gave §7" + amount + "x §fitem §7" + stored.getName() + " §fto player §7"
                + target.getName() + (dropped > 0 ? " §c(" + dropped + " dropped on the ground: inventory full)" : ""));
    }
}
