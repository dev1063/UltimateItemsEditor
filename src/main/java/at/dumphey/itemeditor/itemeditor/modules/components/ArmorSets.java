package at.dumphey.itemeditor.itemeditor.modules.components;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Vanilla set attributes are applied only when the equipped bonus list changes. */
public final class ArmorSets implements Listener {
    private static final String RESOURCE = "armor-sets.yml";
    private static final String PREFIX = "vanilla_set/";
    private static final int INTERVAL = 20;
    private final JavaPlugin plugin;
    private final NamespacedKey setTag;
    private final Map<String, List<Bonus>> definitions = new LinkedHashMap<>();
    private final Map<UUID, List<Bonus>> applied = new HashMap<>();
    private final List<Player> players = new ArrayList<>();
    private final Map<String, Integer> counts = new HashMap<>(4);
    private BukkitTask task;
    private int phase;
    private int cursor;

    public ArmorSets(JavaPlugin plugin) {
        this.plugin = plugin;
        setTag = new NamespacedKey(plugin, "vanilla_armor_set");
    }

    public void start() {
        final File file = new File(plugin.getDataFolder(), RESOURCE);
        try {
            if (!file.exists()) plugin.saveResource(RESOURCE, false);
            final YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(file);
            final ConfigurationSection sets = yaml.getConfigurationSection("sets");
            if (sets != null) {
                for (String id : sets.getKeys(false)) {
                    if (id.matches("[a-z0-9_-]{1,48}")) load(id, sets.getConfigurationSection(id));
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().log(Level.WARNING, "Could not load " + RESOURCE, ex);
        }
        if (definitions.isEmpty()) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerArmorChangeEvent();
        for (Player player : Bukkit.getOnlinePlayers()) clean(player);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void registerArmorChangeEvent() {
        try {
            final Class<? extends Event> type = Class.forName("com.destroystokyo.paper.event.player.PlayerArmorChangeEvent",
                    false, plugin.getClass().getClassLoader()).asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR, (listener, event) -> {
                if (event instanceof PlayerEvent) {
                    final Player player = ((PlayerEvent) event).getPlayer();
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (task != null && player.isOnline()) update(player);
                    });
                }
            }, plugin, true);
        } catch (ClassNotFoundException | LinkageError ignored) {
            // Spigot uses the staggered equipment scan.
        }
    }

    private void load(String id, ConfigurationSection set) {
        if (set == null || !set.getBoolean("enabled", true)) return;
        final List<Bonus> bonuses = new ArrayList<>();
        final List<Map<?, ?>> configured = set.getMapList("bonuses");
        for (int i = 0; i < configured.size(); i++) {
            final Map<?, ?> entry = configured.get(i);
            final Object pieces = entry.get("pieces");
            final Object amount = entry.get("amount");
            final NamespacedKey key = NamespacedKey.fromString(String.valueOf(entry.get("attribute")));
            final Attribute attribute = key == null ? null : Registry.ATTRIBUTE.get(key);
            if (!(pieces instanceof Number) || !(amount instanceof Number) || attribute == null) {
                plugin.getLogger().warning(RESOURCE + " > " + id + " bonus " + (i + 1) + ": invalid fields");
                continue;
            }
            final double required = ((Number) pieces).doubleValue();
            final double value = ((Number) amount).doubleValue();
            if (required < 1 || required > 4 || required != Math.rint(required)
                    || !Double.isFinite(value) || Math.abs(value) > 100) {
                plugin.getLogger().warning(RESOURCE + " > " + id + " bonus " + (i + 1) + ": invalid limits");
                continue;
            }
            final NamespacedKey modifierKey = new NamespacedKey(plugin, PREFIX + id + "/" + i);
            bonuses.add(new Bonus((int) required, Boolean.TRUE.equals(entry.get("exact")), attribute,
                    new AttributeModifier(modifierKey, value, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY)));
        }
        if (!bonuses.isEmpty()) definitions.put(id, bonuses);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public String id(ItemMeta meta) {
        return meta.getPersistentDataContainer().get(setTag, PersistentDataType.STRING);
    }

    public boolean setId(ItemMeta meta, String id) {
        if (id == null) {
            meta.getPersistentDataContainer().remove(setTag);
        } else if (definitions.containsKey(id)) {
            meta.getPersistentDataContainer().set(setTag, PersistentDataType.STRING, id);
        } else {
            return false;
        }
        return true;
    }

    private void tick() {
        if (phase == 0) {
            players.clear();
            players.addAll(Bukkit.getOnlinePlayers());
            cursor = 0;
        }
        final int end = (int) ((players.size() * (long) (phase + 1) + INTERVAL - 1) / INTERVAL);
        while (!players.isEmpty() && cursor < end) {
            final Player player = players.get(cursor++);
            if (player.isOnline()) update(player);
        }
        if (cursor == players.size()) players.clear();
        phase = (phase + 1) % INTERVAL;
    }

    private void update(Player player) {
        counts.clear();
        if (!player.isDead()) {
            for (ItemStack piece : player.getInventory().getArmorContents()) {
                if (piece == null || !piece.hasItemMeta()) continue;
                final String id = id(piece.getItemMeta());
                if (id != null && definitions.containsKey(id)) counts.merge(id, 1, Integer::sum);
            }
        }
        final UUID uuid = player.getUniqueId();
        final List<Bonus> previous = applied.getOrDefault(uuid, Collections.emptyList());
        final List<Bonus> next = counts.isEmpty() ? Collections.emptyList() : new ArrayList<>();
        for (Map.Entry<String, Integer> equipped : counts.entrySet()) {
            final int size = equipped.getValue();
            for (Bonus bonus : definitions.get(equipped.getKey())) {
                if (bonus.exact ? size == bonus.pieces : size >= bonus.pieces) next.add(bonus);
            }
        }
        if (previous.size() == next.size() && previous.containsAll(next)) return;
        for (Bonus bonus : previous) {
            if (!next.contains(bonus)) change(player, bonus, false);
        }
        for (Bonus bonus : next) {
            if (!previous.contains(bonus)) change(player, bonus, true);
        }
        if (next.isEmpty()) applied.remove(uuid);
        else applied.put(uuid, next);
        clampHealth(player);
    }

    private static void change(Player player, Bonus bonus, boolean add) {
        final AttributeInstance attribute = player.getAttribute(bonus.attribute);
        if (attribute == null) return;
        attribute.removeModifier(bonus.modifier);
        if (add) attribute.addModifier(bonus.modifier);
    }

    private void clean(Player player) {
        final String namespace = setTag.getNamespace();
        for (Attribute key : Registry.ATTRIBUTE) {
            final AttributeInstance attribute = player.getAttribute(key);
            if (attribute == null) continue;
            for (AttributeModifier modifier : attribute.getModifiers()) {
                if (modifier.getKey().getNamespace().equals(namespace) && modifier.getKey().getKey().startsWith(PREFIX)) {
                    attribute.removeModifier(modifier);
                }
            }
        }
        clampHealth(player);
    }

    private static void clampHealth(Player player) {
        final AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null && player.getHealth() > max.getValue()) player.setHealth(max.getValue());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clean(event.getPlayer());
        update(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        final List<Bonus> previous = applied.remove(event.getPlayer().getUniqueId());
        if (previous == null) return;
        for (Bonus bonus : previous) change(event.getPlayer(), bonus, false);
        clampHealth(event.getPlayer());
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            final List<Bonus> bonuses = applied.remove(player.getUniqueId());
            if (bonuses != null) {
                for (Bonus bonus : bonuses) change(player, bonus, false);
                clampHealth(player);
            }
        }
        players.clear();
        applied.clear();
        definitions.clear();
    }

    private static final class Bonus {
        final int pieces;
        final boolean exact;
        final Attribute attribute;
        final AttributeModifier modifier;

        Bonus(int pieces, boolean exact, Attribute attribute, AttributeModifier modifier) {
            this.pieces = pieces;
            this.exact = exact;
            this.attribute = attribute;
            this.modifier = modifier;
        }
    }
}
