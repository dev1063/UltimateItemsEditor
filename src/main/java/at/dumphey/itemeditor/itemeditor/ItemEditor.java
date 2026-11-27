package at.dumphey.itemeditor.itemeditor;

import at.dumphey.itemeditor.PluginModules;
import at.dumphey.itemeditor.UltimateItemsEditor;

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
import at.dumphey.itemeditor.ui.template.UiScreen;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ItemEditor implements Listener {

    public static final ItemEditor INSTANCE = new ItemEditor();

    private final UiManager uiManager;
    private final PromptHandler promptHandler;
    private final VersionUtils compatUtils;
    private final ItemStorage itemStorage;
    private final StyleService styleService;
    private final TriggerEngine triggerEngine;
    private final EditorMenu editorMenu;
    private final PluginModules modules;
    private final Map<UUID, ItemStack> editing = new HashMap<>();
    /** Players currently editing a saved item (instead of the item in their hand). */
    private final Map<UUID, SavedEdit> editingSaved = new HashMap<>();

    private static class SavedEdit {
        private final String name;
        private SerializedItem original;
        private final String listCategory;
        private final int listPage;
        private boolean saving;

        private SavedEdit(SerializedItem original, String listCategory, int listPage) {
            this.name = original.getName();
            this.original = original;
            this.listCategory = listCategory;
            this.listPage = listPage;
        }
    }

    public ItemEditor() {
        this.uiManager = new UiManager();
        this.promptHandler = new PromptHandler();
        this.compatUtils = new VersionUtils();
        this.itemStorage = new ItemStorage();
        final UltimateItemsEditor plugin = UltimateItemsEditor.getInstance();
        this.modules = plugin.modules();
        this.editorMenu = EditorMenu.withDefaults(modules.vanilla(), modules.customCommands(), modules.customEnchantments(),
                modules.consumables(), VersionUtils.hasModernItemApi());
        this.styleService = new StyleService(plugin, modules.vanilla() && VersionUtils.hasModernItemApi());
        this.triggerEngine = modules.triggerEngine()
                ? new TriggerEngine(plugin, modules.customCommands(), modules.customEnchantments(), modules.consumables()) : null;
        if (modules.consumables()) {
            triggerEngine.consumables().setLanguageSource(styleService::getLang);
        }
    }

    public PluginModules modules() {
        return modules;
    }

    public void edit(Player player, ItemStack editItem) {
        if (!modules.itemEditor()) {
            uiManager.open(player, new HomeUiScreen(player, true));
            return;
        }
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
    public void editSaved(Player player, SerializedItem saved, String listCategory, int listPage) {
        if (!modules.itemEditor()) {
            return;
        }
        editingSaved.put(player.getUniqueId(), new SavedEdit(saved, listCategory, listPage));
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
        uiManager.open(player, new ItemActionsUiScreen(player, edit.name, edit.listCategory, edit.listPage));
    }

    private void saveEditedItem(Player player) {
        final UUID playerId = player.getUniqueId();
        final SavedEdit edit = editingSaved.get(playerId);
        if (edit.saving) return;
        if (itemStorage.getItem(edit.name) != edit.original) {
            Messages.send(player, "§cLa entrada cambió mientras la editabas. Tus cambios siguen en el editor.");
            return;
        }

        final UltimateItemsEditor plugin = UltimateItemsEditor.getInstance();
        final UiScreen screen = uiManager.getScreen(player);
        final ItemStack snapshot = getItem(player).clone();
        edit.saving = true;
        try {
            final CompletableFuture<Void> saved = itemStorage.saveItem(edit.name, snapshot);
            final SerializedItem accepted = itemStorage.getItem(edit.name);
            saved.whenCompleteAsync((result, error) -> {
                edit.saving = false;
                if (!player.isOnline() || editingSaved.get(playerId) != edit) return;
                if (error != null) {
                    Messages.send(player, "§cError de guardado. Tus cambios siguen en el editor.");
                    return;
                }
                edit.original = accepted;
                Messages.send(player, "§aCambios guardados en §f" + edit.name + "§a.");
                if (uiManager.getScreen(player) == screen && snapshot.equals(getItem(player))) {
                    editingSaved.remove(playerId);
                    editing.remove(playerId);
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1, 1);
                    if (screen != null && screen.isInventory(player.getOpenInventory().getTopInventory())) {
                        uiManager.open(player, new ItemActionsUiScreen(player, edit.name, edit.listCategory, edit.listPage));
                    }
                }
            }, itemStorage.mainThreadExecutor());
        } catch (IOException | IllegalStateException e) {
            edit.saving = false;
            plugin.getLogger().log(Level.SEVERE, "Could not save item " + edit.name, e);
            Messages.send(player, "§cError de guardado. Tus cambios siguen en el editor.");
        }
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

    public CompletableFuture<Boolean> applyTemplateToSaved(String name, Template template) {
        return modifySavedItem(name, item -> styleService.apply(item, template));
    }

    public CompletableFuture<Boolean> resetTemplateOfSaved(String name) {
        return modifySavedItem(name, styleService::reset);
    }

    public CompletableFuture<Integer> applyTemplateToAllSaved(Template template) {
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        applyTemplateBatch(template, itemStorage.getSortedItems().iterator(), 0, result);
        return result;
    }

    private void applyTemplateBatch(Template template, Iterator<SerializedItem> items, int applied,
                                    CompletableFuture<Integer> result) {
        final List<CompletableFuture<Boolean>> batch = new ArrayList<>();
        for (int scanned = 0; scanned < 32 && items.hasNext(); scanned++) {
            final SerializedItem item = items.next();
            if (itemStorage.getItem(item.getName()) == item) {
                batch.add(applyTemplateToSaved(item.getName(), template).exceptionally(error -> false));
            }
        }
        CompletableFuture.allOf(batch.toArray(new CompletableFuture<?>[0])).thenRunAsync(() -> {
            final int total = applied + (int) batch.stream().filter(CompletableFuture::join).count();
            if (items.hasNext()) applyTemplateBatch(template, items, total, result);
            else result.complete(total);
        }, itemStorage.mainThreadExecutor());
    }

    private CompletableFuture<Boolean> modifySavedItem(String name, Predicate<ItemStack> modify) {
        final SerializedItem saved = itemStorage.getItem(name);
        if (saved == null || saved.getItemStack().getType().isAir()) {
            return CompletableFuture.completedFuture(false);
        }
        try {
            final ItemStack copy = saved.getItemStack().clone();
            if (!modify.test(copy)) return CompletableFuture.completedFuture(false);
            return itemStorage.saveItem(name, copy).thenApply(result -> true);
        } catch (IOException | RuntimeException e) {
            UltimateItemsEditor.getInstance().getLogger().log(Level.WARNING, "Could not save item " + name, e);
            final CompletableFuture<Boolean> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    public void onQuit(Player player) {
        if (triggerEngine != null) {
            triggerEngine.onQuit(player.getUniqueId());
        }
        editorMenu.forget(player.getUniqueId());
        editingSaved.remove(player.getUniqueId());
        promptHandler.onQuit(player);
        uiManager.onQuit(player);
    }

    public void openHome(Player player) {
        uiManager.open(player, new HomeUiScreen(player));
    }
}
