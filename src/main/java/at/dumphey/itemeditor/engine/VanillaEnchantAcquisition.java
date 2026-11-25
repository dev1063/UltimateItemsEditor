package at.dumphey.itemeditor.engine;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.styler.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.Configuration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.EnchantingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/** Optional acquisition hooks. The vanilla containers still handle payment and item consumption. */
final class VanillaEnchantAcquisition implements Listener {
    private static final String CONFIG = "custom-enchant-acquisition.";
    private final TriggerEngine engine;
    private final EnchantCatalog catalog;
    private final NamespacedKey bookTag;
    private final Map<Material, List<EnchantCatalog.Entry>> byMaterial = new EnumMap<>(Material.class);
    private final Map<AnvilView, BukkitTask> syncingAnvils = new IdentityHashMap<>();
    private List<EnchantCatalog.Entry> tradeEntries = Collections.emptyList();
    private boolean table;
    private boolean villagers;
    private int tableChance;
    private int tradeChance;
    private int anvilCost;
    private boolean anvilEnabled;
    private boolean inventoryEnabled;
    private int successChance;
    private int destructionChance;
    private boolean destroyItem;
    private final int[] tableWeights = new int[EnchantRarity.values().length];
    private final int[] villagerWeights = new int[EnchantRarity.values().length];

    VanillaEnchantAcquisition(TriggerEngine engine, EnchantCatalog catalog) {
        this.engine = engine;
        this.catalog = catalog;
        this.bookTag = new NamespacedKey(engine.plugin(), "catalog_enchantment_book");
    }

    void start() {
        final Configuration config = engine.plugin().getConfig();
        table = config.getBoolean(CONFIG + "table.enabled", false);
        villagers = config.getBoolean(CONFIG + "villagers.enabled", false);
        tableChance = clamp(config.getInt(CONFIG + "table.chance-percent", 20), 0, 100);
        tradeChance = clamp(config.getInt(CONFIG + "villagers.chance-percent", 20), 0, 100);
        inventoryEnabled = config.getBoolean(CONFIG + "inventory.enabled", true);
        anvilEnabled = config.getBoolean(CONFIG + "anvil.enabled", true);
        successChance = clamp(config.getInt(CONFIG + "inventory.success-percent", 70), 0, 100);
        final int requestedDestruction = clamp(config.getInt(CONFIG + "inventory.destruction-percent", 30), 0, 100);
        destructionChance = Math.min(100 - successChance, requestedDestruction);
        if (destructionChance != requestedDestruction) {
            engine.plugin().getLogger().warning(CONFIG + "inventory: destruction-percent reduced to "
                    + destructionChance + " so success and destruction do not exceed 100%");
        }
        final String mode = config.getString(CONFIG + "inventory.destruction-mode", "enchantment");
        destroyItem = "item".equalsIgnoreCase(mode.trim());
        if (!destroyItem && !"enchantment".equalsIgnoreCase(mode.trim())) {
            engine.plugin().getLogger().warning(CONFIG + "inventory.destruction-mode invalid; using enchantment");
        }
        for (EnchantRarity rarity : EnchantRarity.values()) {
            tableWeights[rarity.ordinal()] = clamp(config.getInt(CONFIG + "table.rarity-weights." + rarity.key(),
                    rarity.tableWeight), 0, 10_000);
            villagerWeights[rarity.ordinal()] = clamp(config.getInt(CONFIG + "villagers.rarity-weights." + rarity.key(),
                    rarity.villagerWeight), 0, 10_000);
        }
        anvilCost = clamp(config.getInt(CONFIG + "anvil.base-level-cost", 3), 1, 32);
        if (villagers) tradeEntries = catalog.entries();
        if (table) {
            for (EnchantCatalog.Entry entry : catalog.entries()) {
                for (Material material : entry.materials()) {
                    byMaterial.computeIfAbsent(material, key -> new ArrayList<>()).add(entry);
                }
            }
        }
        final PluginManager plugins = Bukkit.getPluginManager();
        plugins.registerEvents(this, engine.plugin());
        if (table && tableChance > 0) {
            plugins.registerEvent(EnchantItemEvent.class, this, EventPriority.MONITOR,
                    (listener, event) -> onEnchant((EnchantItemEvent) event), engine.plugin(), true);
        }
        if (villagers && tradeChance > 0) {
            plugins.registerEvent(VillagerAcquireTradeEvent.class, this, EventPriority.HIGHEST,
                    (listener, event) -> onAcquireTrade((VillagerAcquireTradeEvent) event), engine.plugin(), true);
        }
    }

    void stop() {
        HandlerList.unregisterAll(this);
        syncingAnvils.values().forEach(BukkitTask::cancel);
        syncingAnvils.clear();
        byMaterial.clear();
        tradeEntries = Collections.emptyList();
    }

    private void onEnchant(EnchantItemEvent event) {
        if (!table || tableChance == 0 || event.getEnchantsToAdd().isEmpty()) return;
        final ItemStack item = event.getItem();
        final List<EnchantCatalog.Entry> options = byMaterial.get(item.getType());
        if (options == null || !roll(tableChance)) return;

        final int offer = event.whichButton() + 1;
        final Player player = event.getEnchanter();
        if (player.getGameMode() != GameMode.CREATIVE) {
            final ItemStack lapis = ((EnchantingInventory) event.getInventory()).getSecondary();
            if (player.getLevel() < event.getExpLevelCost() || player.getLevel() < offer
                    || lapis == null || lapis.getType() != Material.LAPIS_LAZULI || lapis.getAmount() < offer) return;
        }

        final Book selected = draw(options, engine.tags().read(item), offer, tableWeights,
                ThreadLocalRandom.current()::nextInt);
        if (selected != null && catalog.apply(item, selected.id, selected.level)) {
            player.sendMessage(ChatColor.LIGHT_PURPLE + catalog.get(selected.id).name() + ChatColor.WHITE + " " + selected.level);
        }
    }

    private void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!villagers || tradeEntries.isEmpty() || !(event.getEntity() instanceof Villager)) return;
        final Villager villager = (Villager) event.getEntity();
        final MerchantRecipe original = event.getRecipe();
        if (!Villager.Profession.LIBRARIAN.equals(villager.getProfession())
                || original.getResult().getType() != Material.ENCHANTED_BOOK || !roll(tradeChance)) return;

        final Book selected = draw(tradeEntries, null, villager.getVillagerLevel(), villagerWeights,
                ThreadLocalRandom.current()::nextInt);
        if (selected == null) return;
        final MerchantRecipe replacement = new MerchantRecipe(createBook(catalog.get(selected.id), selected.level),
                original.getUses(), original.getMaxUses(), original.hasExperienceReward(), original.getVillagerExperience(),
                original.getPriceMultiplier(), original.getDemand(), original.getSpecialPrice());
        replacement.setIngredients(original.getIngredients());
        event.setRecipe(replacement);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        final AnvilInventory inventory = event.getInventory();
        final Book book = readBook(inventory.getItem(1));
        if (book == null) return;
        if (!anvilEnabled) {
            event.setResult(null);
            return;
        }
        final AnvilView view = event.getView();
        final ItemStack base = inventory.getItem(0);
        final int cost = cost(base, book);
        final ItemStack result = result(base, book);
        if (result == null || tooExpensive(view, cost)) {
            event.setResult(null);
            return;
        }
        setAnvilCost(view, cost);
        event.setResult(result);

        // Some CraftBukkit versions reset the cost after PrepareAnvilEvent for books with no stored vanilla enchants.
        // Reapply it after the native update, provided the same container and result are still current.
        final BukkitTask previous = syncingAnvils.remove(view);
        if (previous != null) previous.cancel();
        syncingAnvils.put(view, Bukkit.getScheduler().runTask(engine.plugin(), () -> {
            syncingAnvils.remove(view);
            if (view.getPlayer().getOpenInventory() != view) return;
            final Book current = readBook(inventory.getItem(1));
            if (current == null || !current.id.equals(book.id) || current.level != book.level
                    || !result.equals(inventory.getItem(2)) || cost(inventory.getItem(0), current) != cost) return;
            setAnvilCost(view, cost);
        }));
    }

    private static void setAnvilCost(AnvilView view, int cost) {
        view.setRepairCost(cost);
        view.setRepairItemCountCost(1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTakeResult(InventoryClickEvent event) {
        if (inventoryEnabled && event.getClick() == ClickType.RIGHT && event.getView().getType() == InventoryType.CRAFTING
                && event.getWhoClicked() instanceof Player && !(event instanceof org.bukkit.event.inventory.InventoryCreativeEvent)
                && event.getWhoClicked().getInventory().equals(event.getClickedInventory())) {
            final ItemStack cursor = event.getCursor();
            final ItemStack clicked = event.getCurrentItem();
            if (clicked != null && !clicked.getType().isAir()) {
                final Book book = readBook(cursor);
                if (book != null) {
                    applyInInventory(event, (Player) event.getWhoClicked(), clicked, cursor, book);
                    return;
                }
            }
        }
        if (event.getRawSlot() != 2) return;
        if (!villagers && event.getInventory() instanceof MerchantInventory && readBook(event.getCurrentItem()) != null) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getInventory() instanceof AnvilInventory)) return;
        final Book book = readBook(event.getInventory().getItem(1));
        if (book == null) return;
        final AnvilView view = (AnvilView) event.getView();
        final ItemStack base = event.getInventory().getItem(0);
        final int cost = cost(base, book);
        final ItemStack expected = result(base, book);
        if (!anvilEnabled || expected == null || !expected.equals(event.getCurrentItem()) || view.getRepairCost() != cost
                || view.getRepairItemCountCost() != 1 || tooExpensive(view, cost)
                || (view.getPlayer() instanceof Player && view.getPlayer().getGameMode() != GameMode.CREATIVE
                    && ((Player) view.getPlayer()).getLevel() < cost)) {
            event.setCancelled(true);
        }
    }

    private void applyInInventory(InventoryClickEvent event, Player player, ItemStack base, ItemStack cursor, Book book) {
        event.setCancelled(true);
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        if (!canApply(base, book)) {
            player.sendMessage(lang.prefixed("catalog-book-incompatible"));
            return;
        }

        final Outcome result = outcome(ThreadLocalRandom.current().nextInt(100), successChance, destructionChance);
        switch (result) {
            case SUCCESS:
                final ItemStack enchanted = base.clone();
                if (!catalog.apply(enchanted, book.id, book.level)) {
                    player.sendMessage(lang.prefixed("catalog-book-incompatible"));
                    return;
                }
                event.setCurrentItem(enchanted);
                consumeBook(player, cursor);
                player.sendMessage(lang.prefixed("catalog-book-success", "%enchant%", catalog.get(book.id).name(),
                        "%level%", String.valueOf(book.level)));
                break;
            case DESTROYED:
                if (destroyItem) event.setCurrentItem(null);
                consumeBook(player, cursor);
                player.sendMessage(lang.prefixed(destroyItem ? "catalog-book-destroy-item" : "catalog-book-destroy-book"));
                break;
            case FAILED:
                player.sendMessage(lang.prefixed("catalog-book-failed"));
                break;
        }
    }

    private static void consumeBook(Player player, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            player.setItemOnCursor(null);
        } else {
            final ItemStack remaining = cursor.clone();
            remaining.setAmount(cursor.getAmount() - 1);
            player.setItemOnCursor(remaining);
        }
    }

    enum Outcome { SUCCESS, DESTROYED, FAILED }

    static Outcome outcome(int roll, int successChance, int destructionChance) {
        if (roll < successChance) return Outcome.SUCCESS;
        return roll < successChance + destructionChance ? Outcome.DESTROYED : Outcome.FAILED;
    }

    static Book draw(List<EnchantCatalog.Entry> options, ItemTagData tags, int levelCap, int[] weights,
                     IntUnaryOperator random) {
        EnchantCatalog.Entry selected = null;
        int minimum = 1;
        int total = 0;
        for (EnchantCatalog.Entry entry : options) {
            final int weight = weights[entry.rarity().ordinal()];
            if (weight == 0) continue;
            final int high = Math.min(entry.maxLevel(), levelCap);
            final int current = tags == null ? 0 : tags.level(entry.id());
            if (current >= high) continue;
            final int low = current + 1;
            for (int level = low; level <= high; level++) {
                if (weights[entry.rarity(level).ordinal()] > 0) {
                    total += weight;
                    if (random.applyAsInt(total) < weight) {
                        selected = entry;
                        minimum = low;
                    }
                    break;
                }
            }
        }
        if (selected == null) return null;

        int selectedLevel = minimum;
        total = 0;
        for (int level = minimum; level <= Math.min(selected.maxLevel(), levelCap); level++) {
            final int weight = weights[selected.rarity(level).ordinal()];
            if (weight > 0) {
                total += weight;
                if (random.applyAsInt(total) < weight) selectedLevel = level;
            }
        }
        return new Book(selected.id(), selectedLevel);
    }

    private boolean tooExpensive(AnvilView view, int cost) {
        return view.getPlayer().getGameMode() != GameMode.CREATIVE && cost >= view.getMaximumRepairCost();
    }

    private boolean canApply(ItemStack base, Book book) {
        if (base == null || base.getAmount() != 1 || book.level <= 0
                || base.getType() == Material.ENCHANTED_BOOK || base.getType() == Material.BOOK) return false;
        final EnchantCatalog.Entry entry = catalog.get(book.id);
        if (entry == null || book.level > entry.maxLevel() || !entry.accepts(base.getType())) return false;
        final ItemTagData tags = engine.tags().read(base);
        return tags == null || tags.level(book.id) < book.level;
    }

    private ItemStack result(ItemStack base, Book book) {
        if (!canApply(base, book)) return null;
        final ItemStack output = base.clone();
        if (!catalog.apply(output, book.id, book.level)) return null;
        final ItemMeta meta = output.getItemMeta();
        if (meta instanceof Repairable) {
            ((Repairable) meta).setRepairCost((int) Math.min(Integer.MAX_VALUE, 2L * repairPenalty(base) + 1));
            output.setItemMeta(meta);
        }
        return output;
    }

    private int cost(ItemStack base, Book book) {
        return (int) Math.min(Integer.MAX_VALUE, (long) anvilCost + Math.max(0, book.level - 1) + repairPenalty(base));
    }

    private static int repairPenalty(ItemStack base) {
        final ItemMeta meta = base == null ? null : base.getItemMeta();
        return meta instanceof Repairable ? Math.max(0, ((Repairable) meta).getRepairCost()) : 0;
    }

    private ItemStack createBook(EnchantCatalog.Entry entry, int level) {
        final ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        final ItemMeta meta = stack.getItemMeta();
        final EnchantRarity rarity = entry.rarity(level);
        final LanguageManager lang = ItemEditor.INSTANCE.getStyleService().getLang();
        meta.setDisplayName(rarity.color + entry.name() + ChatColor.WHITE + " " + level);
        final List<String> lore = new ArrayList<>();
        lore.add(rarity.color + lang.rawOr("catalog-rarity-" + rarity.key(), rarity.name()));
        if (inventoryEnabled) {
            lore.add(lang.msg("catalog-book-inventory-hint"));
        }
        if (anvilEnabled) {
            lore.add(lang.msg("catalog-book-anvil-hint"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(bookTag, PersistentDataType.STRING, entry.id() + ":" + level);
        stack.setItemMeta(meta);
        return stack;
    }

    private Book readBook(ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return null;
        final ItemMeta meta = item.getItemMeta();
        final String value = meta.getPersistentDataContainer().get(bookTag, PersistentDataType.STRING);
        if (value == null) return null;

        if (value.length() > 56) return Book.INVALID;
        final int separator = value.lastIndexOf(':');
        if (separator <= 0 || !(meta instanceof EnchantmentStorageMeta)
                || ((EnchantmentStorageMeta) meta).hasStoredEnchants()) return Book.INVALID;
        final String id = value.substring(0, separator);
        if (!ItemTags.isValidId(id)) return Book.INVALID;
        try {
            return new Book(id, Integer.parseInt(value.substring(separator + 1)));
        } catch (NumberFormatException ex) {
            return Book.INVALID;
        }
    }

    private static boolean roll(int percentage) {
        return ThreadLocalRandom.current().nextInt(100) < percentage;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class Book {
        static final Book INVALID = new Book("", 0);
        final String id;
        final int level;

        Book(String id, int level) {
            this.id = id;
            this.level = level;
        }
    }
}
