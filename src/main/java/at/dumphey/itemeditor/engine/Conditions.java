package at.dumphey.itemeditor.engine;

import at.dumphey.itemeditor.styler.ColorFormat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The built-in conditions. Add your own with {@link TriggerEngine#registerCondition}. */
public final class Conditions {

    private static final Pattern COMPARE = Pattern.compile("^\\s*([a-z_\\-]+)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");
    private static final Set<String> COMPARE_VARS = Cfg.lowerSet(java.util.Arrays.asList("damage", "level", "health",
            "food", "xp-level", "x", "y", "z", "item-amount", "target-health"));

    private Conditions() {
    }

    public static void registerDefaults(TriggerEngine engine) {
        engine.registerCondition("world", (cfg, pc) -> {
            final Set<String> worlds = nonEmpty(Cfg.nameSet(cfg), pc);
            return ctx -> worlds.contains(ctx.player.getWorld().getName());
        });
        engine.registerCondition("click", (cfg, pc) -> {
            int mask = 0;
            for (Object o : Cfg.list(cfg)) {
                final int m = ClickKind.maskOf(String.valueOf(o));
                if (m == 0) {
                    throw pc.error("unknown click '" + o + "' (left, right, air, block, left-air, left-block, right-air, right-block)");
                }
                mask |= m;
            }
            final int allowed = mask;
            return ctx -> ctx.click != null && (allowed & ctx.click.bit()) != 0;
        });
        engine.registerCondition("slot", (cfg, pc) -> {
            final int mask = Slots.parse(cfg, pc);
            return ctx -> (mask & Slots.bit(ctx.slot)) != 0;
        });
        engine.registerCondition("hotbar-slot", (cfg, pc) -> {
            int slots = 0;
            for (Object entry : Cfg.list(cfg)) {
                final double value = Cfg.number(entry, pc);
                if (!(value >= 1 && value <= 9) || value != (int) value) {
                    throw pc.error("expected hotbar positions 1 to 9");
                }
                slots |= 1 << ((int) value - 1);
            }
            if (slots == 0) throw pc.error("expected at least one hotbar position");
            final int mask = slots;
            return ctx -> ctx.slot == org.bukkit.inventory.EquipmentSlot.HAND
                    && (mask & (1 << ctx.player.getInventory().getHeldItemSlot())) != 0;
        });
        engine.registerCondition("sneaking", (cfg, pc) -> {
            final boolean expected = Cfg.bool(cfg, pc);
            return ctx -> ctx.player.isSneaking() == expected;
        });
        engine.registerCondition("sprinting", (cfg, pc) -> {
            final boolean expected = Cfg.bool(cfg, pc);
            return ctx -> ctx.player.isSprinting() == expected;
        });
        engine.registerCondition("flying", (cfg, pc) -> {
            final boolean expected = Cfg.bool(cfg, pc);
            return ctx -> ctx.player.isFlying() == expected;
        });
        engine.registerCondition("gamemode", (cfg, pc) -> {
            final Set<String> modes = nonEmpty(Cfg.upperSet(cfg), pc);
            return ctx -> modes.contains(ctx.player.getGameMode().name());
        });
        engine.registerCondition("permission", (cfg, pc) -> {
            final List<String> perms = new ArrayList<>();
            for (Object o : Cfg.list(cfg)) {
                perms.add(String.valueOf(o));
            }
            if (perms.isEmpty()) {
                throw pc.error("no permission given");
            }
            return ctx -> {
                for (String perm : perms) {
                    if (!ctx.player.hasPermission(perm)) {
                        return false;
                    }
                }
                return true;
            };
        });
        engine.registerCondition("chance", (cfg, pc) -> {
            final double percent = Cfg.number(cfg, pc);
            if (percent < 0 || percent > 100) {
                throw pc.error("chance must be between 0 and 100 (percent), got " + percent);
            }
            return ctx -> ThreadLocalRandom.current().nextDouble() * 100.0 < percent;
        });
        engine.registerCondition("melee", (cfg, pc) -> {
            final boolean required = Cfg.bool(cfg, pc);
            return ctx -> ctx.event instanceof EntityDamageByEntityEvent
                    && (((EntityDamageByEntityEvent) ctx.event).getDamager()
                    instanceof LivingEntity) == required;
        });
        engine.registerCondition("compare", Conditions::compare);
        engine.registerCondition("item-material", (cfg, pc) -> {
            final Set<String> materials = Cfg.upperSet(cfg);
            if (materials.isEmpty()) {
                throw pc.error("no material given");
            }
            for (String m : materials) {
                if (Material.matchMaterial(m) == null) {
                    throw pc.error("unknown material '" + m + "'");
                }
            }
            return ctx -> ctx.item != null && materials.contains(ctx.item.getType().name());
        });
        engine.registerCondition("item-id", (cfg, pc) -> {
            final Set<String> ids = nonEmpty(Cfg.lowerSet(cfg), pc);
            return ctx -> {
                final ItemTagData tag = ctx.tags();
                return tag != null && tag.getItemId() != null && ids.contains(tag.getItemId());
            };
        });
        engine.registerCondition("custom-enchant", (cfg, pc) -> {
            if (!engine.acceptsRule(true)) {
                throw pc.error("modules.custom-enchantments is disabled");
            }
            final List<String> ids = new ArrayList<>();
            final List<Integer> mins = new ArrayList<>();
            for (Object o : Cfg.list(cfg)) {
                final String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
                final int colon = s.indexOf(':');
                final String id = colon < 0 ? s : s.substring(0, colon);
                int min = 1;
                if (colon >= 0) {
                    try {
                        min = Integer.parseInt(s.substring(colon + 1).trim());
                    } catch (NumberFormatException e) {
                        throw pc.error("'" + o + "': the level after ':' must be a whole number");
                    }
                }
                if (!ItemTags.isValidId(id)) {
                    throw pc.error("invalid enchantment id '" + id + "' (a-z, 0-9, _ and -)");
                }
                ids.add(id);
                mins.add(min);
            }
            if (ids.isEmpty()) {
                throw pc.error("no enchantment given");
            }
            return ctx -> {
                final ItemTagData tag = ctx.tags();
                if (tag == null) {
                    return false;
                }
                for (int i = 0; i < ids.size(); i++) {
                    if (tag.level(ids.get(i)) < mins.get(i)) {
                        return false;
                    }
                }
                return true;
            };
        });
        engine.registerCondition("vanilla-enchant", (cfg, pc) -> {
            final List<Enchantment> enchants = new ArrayList<>();
            final List<Integer> mins = new ArrayList<>();
            for (Object o : Cfg.list(cfg)) {
                final String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
                final int colon = s.indexOf(':');
                final String name = colon < 0 ? s : s.substring(0, colon);
                final Enchantment enchant = Enchantment.getByKey(NamespacedKey.minecraft(name));
                if (enchant == null) {
                    throw pc.error("unknown enchantment '" + name + "'");
                }
                int min = 1;
                if (colon >= 0) {
                    try {
                        min = Integer.parseInt(s.substring(colon + 1).trim());
                    } catch (NumberFormatException e) {
                        throw pc.error("'" + o + "': the level after ':' must be a whole number");
                    }
                }
                enchants.add(enchant);
                mins.add(min);
            }
            if (enchants.isEmpty()) {
                throw pc.error("no enchantment given");
            }
            return ctx -> {
                if (ctx.item == null) {
                    return false;
                }
                for (int i = 0; i < enchants.size(); i++) {
                    if (ctx.item.getEnchantmentLevel(enchants.get(i)) < mins.get(i)) {
                        return false;
                    }
                }
                return true;
            };
        });
        engine.registerCondition("item-name-contains", (cfg, pc) -> {
            final List<String> needles = needles(cfg, pc);
            return ctx -> {
                final ItemMeta meta = ctx.item == null || !ctx.item.hasItemMeta() ? null : ctx.item.getItemMeta();
                return meta != null && meta.hasDisplayName() && containsAny(ColorFormat.strip(meta.getDisplayName()), needles);
            };
        });
        engine.registerCondition("item-lore-contains", (cfg, pc) -> {
            final List<String> needles = needles(cfg, pc);
            return ctx -> {
                final ItemMeta meta = ctx.item == null || !ctx.item.hasItemMeta() ? null : ctx.item.getItemMeta();
                if (meta == null || !meta.hasLore()) {
                    return false;
                }
                for (String line : meta.getLore()) {
                    if (containsAny(ColorFormat.strip(line), needles)) {
                        return true;
                    }
                }
                return false;
            };
        });
        engine.registerCondition("block-type", (cfg, pc) -> {
            final Set<String> materials = Cfg.upperSet(cfg);
            if (materials.isEmpty()) {
                throw pc.error("no block given");
            }
            for (String m : materials) {
                if (Material.matchMaterial(m) == null) {
                    throw pc.error("unknown material '" + m + "'");
                }
            }
            return ctx -> ctx.block != null && materials.contains(ctx.block.getType().name());
        });
        engine.registerCondition("mythic-mob", (cfg, pc) -> {
            final Set<String> ids = new java.util.HashSet<>();
            for (Object value : Cfg.list(cfg)) {
                if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
                    throw pc.error("expected a MythicMobs internal ID");
                }
                ids.add(((String) value).trim().toLowerCase(Locale.ROOT));
            }
            if (ids.isEmpty()) throw pc.error("expected one or more MythicMobs internal IDs");
            return ctx -> {
                final String id = ctx.mythicTargetId();
                return id != null && ids.contains(id);
            };
        });
        engine.registerCondition("target-type", (cfg, pc) -> {
            final Set<String> types = Cfg.upperSet(cfg);
            if (types.isEmpty()) {
                throw pc.error("no entity type given");
            }
            for (String t : types) {
                try {
                    EntityType.valueOf(t);
                } catch (IllegalArgumentException e) {
                    throw pc.error("unknown entity type '" + t + "'");
                }
            }
            return ctx -> ctx.target != null && types.contains(ctx.target.getType().name());
        });
        engine.registerCondition("target-player", (cfg, pc) -> {
            final boolean expected = Cfg.bool(cfg, pc);
            return ctx -> (ctx.target instanceof Player) == expected;
        });
        engine.registerCondition("time", (cfg, pc) -> {
            final String s = String.valueOf(cfg).trim().toLowerCase(Locale.ROOT);
            if (!s.equals("day") && !s.equals("night")) {
                throw pc.error("expected 'day' or 'night'");
            }
            final boolean day = s.equals("day");
            return ctx -> (ctx.player.getWorld().getTime() % 24000 < 12000) == day;
        });
        engine.registerCondition("cooldown", (cfg, pc) -> {
            final String key = String.valueOf(cfg).trim();
            if (key.isEmpty()) {
                throw pc.error("no cooldown key given");
            }
            final TriggerEngine eng = pc.engine();
            return ctx -> eng.cooldowns().isReady(ctx.player.getUniqueId(), key);
        });
        engine.registerCondition("all", (cfg, pc) -> {
            final Condition c = parseAll(cfg, pc);
            return c == null ? ctx -> true : c;
        });
        engine.registerCondition("any", (cfg, pc) -> {
            final List<Condition> list = parseEach(cfg, pc);
            if (list.isEmpty()) {
                throw pc.error("'any' needs at least one condition");
            }
            final Condition[] array = list.toArray(new Condition[0]);
            return ctx -> {
                for (Condition c : array) {
                    if (c.test(ctx)) {
                        return true;
                    }
                }
                return false;
            };
        });
        engine.registerCondition("not", (cfg, pc) -> {
            final Condition c = parseAll(cfg, pc);
            if (c == null) {
                throw pc.error("'not' needs at least one condition");
            }
            return ctx -> !c.test(ctx);
        });
    }

    /** A list of {@code name: value} entries joined with AND. Null if the list is empty. */
    public static Condition parseAll(Object value, ParseContext pc) throws ConfigException {
        final List<Condition> list = parseEach(value, pc);
        if (list.isEmpty()) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        final Condition[] array = list.toArray(new Condition[0]);
        return ctx -> {
            for (Condition c : array) {
                if (!c.test(ctx)) {
                    return false;
                }
            }
            return true;
        };
    }

    private static List<Condition> parseEach(Object value, ParseContext pc) throws ConfigException {
        final List<Object> entries = Cfg.list(value);
        final List<Condition> list = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            final ParseContext ipc = pc.index(i);
            final Map<String, Object> map = Cfg.map(entries.get(i));
            if (map == null) {
                throw ipc.error("expected 'name: value' but found '" + entries.get(i) + "'");
            }
            for (Map.Entry<String, Object> e : map.entrySet()) {
                list.add(pc.engine().parseCondition(e.getKey(), e.getValue(), ipc.child(e.getKey())));
            }
        }
        return list;
    }

    private static Condition compare(Object cfg, ParseContext pc) throws ConfigException {
        final Matcher m = COMPARE.matcher(String.valueOf(cfg).toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            throw pc.error("expected something like \"damage >= 5\" (variables: " + String.join(", ", COMPARE_VARS) + ")");
        }
        final String var = m.group(1);
        if (!COMPARE_VARS.contains(var)) {
            throw pc.error("unknown variable '" + var + "' (variables: " + String.join(", ", COMPARE_VARS) + ")");
        }
        final double value = Double.parseDouble(m.group(3));
        final String op = m.group(2);
        return ctx -> {
            final double v = variable(var, ctx);
            switch (op) {
                case ">=":
                    return v >= value;
                case "<=":
                    return v <= value;
                case ">":
                    return v > value;
                case "<":
                    return v < value;
                case "==":
                    return v == value;
                default:
                    return v != value;
            }
        };
    }

    private static double variable(String name, TriggerContext ctx) {
        switch (name) {
            case "damage":
                return ctx.getDamage();
            case "level":
                return ctx.level;
            case "health":
                return ctx.player.getHealth();
            case "food":
                return ctx.player.getFoodLevel();
            case "xp-level":
                return ctx.player.getLevel();
            case "x":
                return ctx.location().getX();
            case "y":
                return ctx.location().getY();
            case "z":
                return ctx.location().getZ();
            case "item-amount":
                return ctx.item == null ? 0 : ctx.item.getAmount();
            default: // target-health
                return ctx.target instanceof org.bukkit.entity.LivingEntity ? ((org.bukkit.entity.LivingEntity) ctx.target).getHealth() : 0;
        }
    }

    private static Set<String> nonEmpty(Set<String> set, ParseContext pc) throws ConfigException {
        if (set.isEmpty()) {
            throw pc.error("no value given");
        }
        return set;
    }

    private static List<String> needles(Object cfg, ParseContext pc) throws ConfigException {
        final List<String> list = new ArrayList<>();
        for (Object o : Cfg.list(cfg)) {
            list.add(ColorFormat.strip(String.valueOf(o)).toLowerCase(Locale.ROOT));
        }
        if (list.isEmpty()) {
            throw pc.error("no text given");
        }
        return list;
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        final String lower = haystack.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (lower.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
