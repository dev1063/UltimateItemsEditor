package at.dumphey.itemeditor.itemeditor;

import at.dumphey.itemeditor.compatibility.VersionUtils;
import at.dumphey.itemeditor.itemeditor.modules.home.HomeUiScreen;
import at.dumphey.itemeditor.itemeditor.modules.list.ItemActionsUiScreen;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.itemeditor.storage.ItemStorage;
import at.dumphey.itemeditor.engine.TriggerEngine;
import at.dumphey.itemeditor.itemeditor.modules.home.EditorMenu;
import at.dumphey.itemeditor.styler.StyleService;
import at.dumphey.itemeditor.styler.model.Template;
import at.dumphey.itemeditor.ui.prompts.PromptHandler;
import at.dumphey.itemeditor.ui.UiManager;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.utils.Messages;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ItemEditor implements Listener {

    public static final ItemEditor INSTANCE = new ItemEditor();

    private final UiManager uiManager;
    private final PromptHandler promptHandler;
    private final VersionUtils compatUtils;
    private final ItemStorage itemStorage;
    private final StyleService styleService;
    private final TriggerEngine triggerEngine;
    private final EditorMenu editorMenu = EditorMenu.withDefaults();
    private final Map<UUID, ItemStack> editing = new HashMap<>();
    /** Players currently editing a saved item (instead of the item in their hand). */
    private final Map<UUID, SavedEdit> editingSaved = new HashMap<>();

    private static class SavedEdit {
        private final String name;
        private final int listPage;

        private SavedEdit(String name, int listPage) {
            this.name = name;
            this.listPage = listPage;
        }
    }

    public ItemEditor() {
        this.uiManager = new UiManager();
        this.promptHandler = new PromptHandler();
        this.compatUtils = new VersionUtils();
        this.itemStorage = new ItemStorage();
        this.styleService = new StyleService(at.dumphey.itemeditor.UltimateItemsEditor.getInstance());
        this.triggerEngine = new TriggerEngine(at.dumphey.itemeditor.UltimateItemsEditor.getInstance());
        this.triggerEngine.consumables().setLanguageSource(() -> this.styleService.getLang());
    }

    public void edit(Player player, ItemStack editItem) {
        editingSaved.remove(player.getUniqueId());
        setItem(player, editItem);
        uiManager.open(player, new HomeUiScreen(player));
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
    }

    public ItemStack getItem(Player player) {
        return editing.get(player.getUniqueId());
    }

    public void setItem(Player player, ItemStack itemStack) {
        editing.put(player.getUniqueId(), itemStack);
    }

    /**
     * Opens the editor for a saved item. The player's hand is not touched; the changes are written back to the
     * saved entry when they finish (see {@link #finish(Player)}) or discarded on {@link #cancelSavedEdit(Player)}.
     */
    public void editSaved(Player player, SerializedItem saved, int listPage) {
        editingSaved.put(player.getUniqueId(), new SavedEdit(saved.getName(), listPage));
        setItem(player, saved.getItemStack().clone()); // clone: the editor mutates the stack in place
        uiManager.open(player, new HomeUiScreen(player));
    }

    public boolean isEditingSaved(Player player) {
        return editingSaved.containsKey(player.getUniqueId());
    }

    public String getEditingSavedName(Player player) {
        final SavedEdit edit = editingSaved.get(player.getUniqueId());
        return edit == null ? null : edit.name;
    }

    public void cancelSavedEdit(Player player) {
        final SavedEdit edit = editingSaved.remove(player.getUniqueId());
        editing.remove(player.getUniqueId());
        if (edit == null) {
            uiManager.close(player);
            return;
        }
        uiManager.open(player, new ItemActionsUiScreen(player, edit.name, edit.listPage));
    }

    private void saveEditedItem(Player player) {
        final SavedEdit edit = editingSaved.get(player.getUniqueId());
        try {
            itemStorage.saveItem(edit.name, getItem(player).clone());
        } catch (IOException e) {
            at.dumphey.itemeditor.UltimateItemsEditor.getInstance().getLogger()
                    .log(Level.SEVERE, "Could not save item " + edit.name, e);
            Messages.send(player, "§cSaving failed. Your changes are still open in the editor - check the console.");
            return;
        }

        editingSaved.remove(player.getUniqueId());
        editing.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1, 1);
        Messages.send(player, "Saved changes to §7" + edit.name + "§f.");
        uiManager.open(player, new ItemActionsUiScreen(player, edit.name, edit.listPage));
    }

    /**
     * Gives {@code amount} of a saved item to a player. What does not fit in the inventory is dropped at the
     * player's feet.
     *
     * @return the number of items that were dropped on the ground
     */
    public int giveItem(Player target, SerializedItem item, int amount) {
        final ItemStack toGive = item.getItemStack().clone();
        if (toGive.getType() == Material.AIR) {
            return 0; // broken entry (unknown material): nothing can be given
        }
        toGive.setAmount(amount);

        final Map<Integer, ItemStack> leftover = target.getInventory().addItem(personalizeItem(toGive, target));
        int dropped = 0;
        for (ItemStack rest : leftover.values()) {
            int remaining = rest.getAmount();
            final int maxStack = Math.max(1, rest.getMaxStackSize());
            while (remaining > 0) {
                final ItemStack part = rest.clone();
                part.setAmount(Math.min(remaining, maxStack));
                target.getWorld().dropItemNaturally(target.getLocation(), part);
                remaining -= part.getAmount();
                dropped += part.getAmount();
            }
        }
        return dropped;
    }

    public void finish(Player player) {
        if (isEditingSaved(player)) {
            saveEditedItem(player);
            return;
        }

        final ItemStack item = getItem(player);
        uiManager.close(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1, 1);
        player.getInventory().setItemInMainHand(item);
    }

    public UiManager getUiManager() {
        return uiManager;
    }

    public PromptHandler getPromptHandler() {
        return promptHandler;
    }

    public VersionUtils getCompatUtils() {
        return compatUtils;
    }

    public boolean isEditing(Player player) {
        return editing.containsKey(player.getUniqueId());
    }

    public void modifyItem(Player player, Function<ItemStack, ItemStack> modifier) {
        setItem(player, modifier.apply(getItem(player)));
        uiManager.update(player);
    }

    public ItemStack personalizeItem(ItemStack itemStack, Player player) {
        ItemStack result = itemStack.clone();
        if (!at.dumphey.itemeditor.UltimateItemsEditor.getInstance().isPlaceholderApiEnabled()) {
            return result;
        }

        ItemBuilder builder = ItemBuilder.of(result);
        if (!result.hasItemMeta() || result.getItemMeta() == null) {
            return result;
        }

        if (result.getItemMeta().hasDisplayName()) {
            builder = builder.withName(PlaceholderAPI.setPlaceholders(player, result.getItemMeta().getDisplayName()));
        }

        if (result.getItemMeta().hasLore() && result.getItemMeta().getLore() != null) {
            builder = builder.withLore(result.getItemMeta().getLore().stream().map(l -> PlaceholderAPI.setPlaceholders(player, l)).collect(Collectors.toList()));
        }

        return builder.build();
    }

    public ItemStorage getItemStorage() {
        return itemStorage;
    }

    /** The categories and options of the editor home menu: register yours here. */
    public EditorMenu getEditorMenu() {
        return editorMenu;
    }

    /** Triggers, conditions and actions for custom items and enchantments (loaded in the plugin's onEnable). */
    public TriggerEngine getTriggerEngine() {
        return triggerEngine;
    }

    /** Templates, language and enchantment/attribute styles (loaded in the plugin's onEnable). */
    public StyleService getStyleService() {
        return styleService;
    }

    /** Applies a template to a saved item and stores the result. Returns false if it is missing or cannot be saved. */
    public boolean applyTemplateToSaved(String name, Template template) {
        final SerializedItem saved = itemStorage.getItem(name);
        if (saved == null) {
            return false;
        }
        final ItemStack copy = saved.getItemStack().clone();
        return styleService.apply(copy, template) && saveQuietly(name, copy);
    }

    /** Takes the template off a saved item. Returns false if it has none, is missing or cannot be saved. */
    public boolean resetTemplateOfSaved(String name) {
        final SerializedItem saved = itemStorage.getItem(name);
        if (saved == null) {
            return false;
        }
        final ItemStack copy = saved.getItemStack().clone();
        return styleService.reset(copy) && saveQuietly(name, copy);
    }

    /** Applies a template to every saved item. Returns how many items were updated. */
    public int applyTemplateToAllSaved(Template template) {
        int count = 0;
        for (SerializedItem saved : new ArrayList<>(itemStorage.getItems())) {
            if (applyTemplateToSaved(saved.getName(), template)) {
                count++;
            }
        }
        return count;
    }

    private boolean saveQuietly(String name, ItemStack item) {
        try {
            itemStorage.saveItem(name, item);
            return true;
        } catch (IOException e) {
            at.dumphey.itemeditor.UltimateItemsEditor.getInstance().getLogger()
                    .log(Level.SEVERE, "Could not save item " + name, e);
            return false;
        }
    }

    public void onQuit(Player player) {
        triggerEngine.onQuit(player.getUniqueId());
        editorMenu.forget(player.getUniqueId());
        editingSaved.remove(player.getUniqueId());
        promptHandler.onQuit(player);
        uiManager.onQuit(player);
    }

    public void openHome(Player player) {
        uiManager.open(player, new HomeUiScreen(player));
    }
}
