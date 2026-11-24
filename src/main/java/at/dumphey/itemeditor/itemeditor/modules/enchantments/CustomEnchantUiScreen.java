package at.dumphey.itemeditor.itemeditor.modules.enchantments;

import at.dumphey.itemeditor.engine.EnchantCatalog;
import at.dumphey.itemeditor.engine.ItemTagData;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.home.HomeUiScreen;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.items.StaticUiItem;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Applies catalog levels to the editor item, using the same persistent tags as the rule engine. */
public final class CustomEnchantUiScreen extends UiScreen {
    private static final int PAGE_SIZE = 45;
    private final String selection;
    private int page;

    public CustomEnchantUiScreen(Player player) {
        this(player, null, 0);
    }

    private CustomEnchantUiScreen(Player player, String selection, int page) {
        super(player, 54, selection == null ? "Encantamientos custom" : "Nivel del encantamiento");
        this.selection = selection;
        this.page = page;
    }

    @Override
    protected void onUpdate() {
        final ItemStack item = getEditedItem();
        final EnchantCatalog catalog = ItemEditor.INSTANCE.getTriggerEngine().enchantCatalog();
        final ItemTagData data = ItemEditor.INSTANCE.getTriggerEngine().tags().read(item);
        final Map<String, Integer> applied = data == null ? Collections.emptyMap() : data.getEnchants();
        button(53, Material.ARROW, "Volver", () -> {
            if (selection == null) {
                open(new HomeUiScreen(player));
            } else {
                open(new CustomEnchantUiScreen(player, null, page));
            }
        });
        if (item == null || item.getType().isAir()) {
            info("Abre el editor con un ítem.");
        } else if (selection == null) {
            renderCatalog(item, catalog, applied);
        } else {
            renderLevels(item, catalog, applied.getOrDefault(selection, 0));
        }
        fillEmptyWithFiller();
    }

    private void renderCatalog(ItemStack item, EnchantCatalog catalog, Map<String, Integer> applied) {
        final List<String> ids = new ArrayList<>();
        for (EnchantCatalog.Entry entry : catalog.entries()) {
            if (entry.accepts(item.getType()) || applied.containsKey(entry.id())) {
                ids.add(entry.id());
            }
        }
        for (String id : applied.keySet()) {
            if (catalog.get(id) == null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            info("No hay encantamientos compatibles.");
            return;
        }
        final int pages = PageBar.pages(ids.size(), PAGE_SIZE);
        page = Math.min(page, pages - 1);
        for (int i = page * PAGE_SIZE; i < Math.min(ids.size(), (page + 1) * PAGE_SIZE); i++) {
            final String id = ids.get(i);
            final EnchantCatalog.Entry entry = catalog.get(id);
            button(i % PAGE_SIZE, Material.ENCHANTED_BOOK, entry == null ? id : entry.name(),
                    () -> open(new CustomEnchantUiScreen(player, id, page)),
                    "ID: " + id, "Nivel aplicado: " + applied.getOrDefault(id, 0));
        }
        if (pages > 1) {
            final UiItem[] controls = PageBar.create(this, player, page, pages, "", next -> {
                page = next;
                update();
            });
            for (int i = 0; i < controls.length; i++) {
                setItem(48 + i, controls[i]);
            }
        }
    }

    private void renderLevels(ItemStack item, EnchantCatalog catalog, int appliedLevel) {
        final EnchantCatalog.Entry entry = catalog.get(selection);
        if (entry != null && entry.accepts(item.getType())) {
            for (int i = 1; i <= entry.maxLevel(); i++) {
                final int level = i;
                button(i - 1, Material.ENCHANTED_BOOK, entry.name() + " " + level,
                        () -> apply(catalog, level), level == appliedLevel ? "Nivel actual" : "Clic para aplicar");
            }
        } else {
            info("Encantamiento no disponible para este ítem.");
        }
        if (appliedLevel > 0) {
            button(45, Material.BARRIER, "Quitar encantamiento", () -> apply(catalog, 0));
        }
    }

    private void apply(EnchantCatalog catalog, int level) {
        final ItemStack edited = getEditedItem();
        if (edited != null) {
            final ItemStack copy = edited.clone();
            if (catalog.apply(copy, selection, level)) {
                ItemEditor.INSTANCE.setItem(player, copy);
                update();
                return;
            }
        }
        player.sendMessage(ItemEditor.INSTANCE.getStyleService().getLang().prefixed("catalog-incompatible"));
    }

    private void info(String label) {
        setItem(22, new StaticUiItem(this, player, ItemBuilder.uiItem(Material.PAPER, label)));
    }

    private void button(int slot, Material icon, String name, Runnable action, String... lore) {
        setItem(slot, new ActionUiItem(this, player, ItemBuilder.uiItem(icon, name, lore), action));
    }
}
