package at.dumphey.itemeditor.engine;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The built-in actions. Durations accept 5s, 20t, 2m, 500ms; a bare number is SECONDS.
 * Add your own with {@link TriggerEngine#registerAction}.
 */
public final class Actions {

    /** No delay longer than one hour: a typo like "delay: 100000" must not park tasks for days. */
    private static final int MAX_DELAY_TICKS = 20 * 3600;
    private static final Pattern SOUND_KEY = Pattern.compile("[a-z0-9_.:/\\-]+");

    private Actions() {
    }

    /** Which entity an action works on. */
    private enum Who {
        SELF, TARGET;

        Entity resolve(TriggerContext ctx) {
            return this == SELF ? ctx.player : ctx.target;
        }
    }

    public static void registerDefaults(TriggerEngine engine) {
        engine.registerAction("message", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            String text = String.valueOf(cfg);
            Who who = Who.SELF;
            if (map != null) {
                Cfg.checkKeys(map, pc, "text", "target");
                if (map.get("text") == null) {
                    throw pc.error("missing 'text'");
                }
                text = String.valueOf(map.get("text"));
                who = who(map.get("target"), Who.SELF, pc.child("target"));
            }
            final Template template = Template.compile(text, true);
            final Who target = who;
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e instanceof Player) {
                    ((Player) e).sendMessage(template.render(ctx));
                }
            };
        });

        engine.registerAction("actionbar", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map != null) {
                Cfg.checkKeys(map, pc, "text", "target");
            }
            final Template text = displayText(map == null ? cfg : map.get("text"), pc.child("text"));
            final Who target = who(map == null ? null : map.get("target"), Who.SELF, pc.child("target"));
            final BaseComponent[] constant = text.isConstant() ? TextComponent.fromLegacyText(text.render(null)) : null;
            return ctx -> {
                final Entity recipient = target.resolve(ctx);
                if (recipient instanceof Player) {
                    ((Player) recipient).spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            constant != null ? constant : TextComponent.fromLegacyText(text.render(ctx)));
                }
            };
        });

        engine.registerAction("title", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map != null) {
                Cfg.checkKeys(map, pc, "text", "subtitle", "fade-in", "stay", "fade-out", "target");
            }
            final Template title = displayText(map == null ? cfg : map.getOrDefault("text", ""), pc.child("text"));
            final Template subtitle = displayText(map == null ? "" : map.getOrDefault("subtitle", ""), pc.child("subtitle"));
            final int fadeIn = titleTicks(map, "fade-in", 10, pc);
            final int stay = titleTicks(map, "stay", 40, pc);
            final int fadeOut = titleTicks(map, "fade-out", 10, pc);
            final Who target = who(map == null ? null : map.get("target"), Who.SELF, pc.child("target"));
            return ctx -> {
                final Entity recipient = target.resolve(ctx);
                if (recipient instanceof Player) {
                    ((Player) recipient).sendTitle(title.render(ctx), subtitle.render(ctx), fadeIn, stay, fadeOut);
                }
            };
        });

        engine.registerAction("particle", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map != null) {
                Cfg.checkKeys(map, pc, "particle", "count", "spread", "height", "speed", "target");
            }
            String name = String.valueOf(map == null ? cfg : map.get("particle")).trim().toLowerCase(Locale.ROOT);
            if (name.startsWith("minecraft:")) {
                name = name.substring("minecraft:".length());
            }
            final Particle particle = particleByName(name, pc);
            if (particle.getDataType() != Void.class) {
                throw pc.child("particle").error("'" + name + "' requires extra data; use a particle such as flame, heart or happy_villager");
            }

            final double amount = effectNumber(map, "count", 12, 1, 128, pc);
            if (amount != (int) amount) {
                throw pc.child("count").error("expected a whole number");
            }
            final int count = (int) amount;
            final double spread = effectNumber(map, "spread", 0.25, 0, 8, pc);
            final double height = effectNumber(map, "height", 1, -8, 8, pc);
            final double speed = effectNumber(map, "speed", 0, 0, 4, pc);
            final Who target = who(map == null ? null : map.get("target"), Who.SELF, pc.child("target"));
            return ctx -> {
                final Entity origin = target.resolve(ctx);
                if (origin != null) {
                    final Location location = origin.getLocation().add(0, height, 0);
                    location.getWorld().spawnParticle(particle, location, count, spread, spread, spread, speed);
                }
            };
        });

        engine.registerAction("command", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            Object value = cfg;
            boolean asPlayer = false;
            if (map != null) {
                Cfg.checkKeys(map, pc, "command", "as");
                if (map.get("command") == null) {
                    throw pc.error("missing 'command'");
                }
                value = map.get("command");
                final String as = String.valueOf(map.getOrDefault("as", "console")).toLowerCase(Locale.ROOT);
                if (!as.equals("console") && !as.equals("player")) {
                    throw pc.child("as").error("expected 'console' or 'player'");
                }
                asPlayer = as.equals("player");
            }
            if (!(value instanceof String)) {
                throw pc.error("expected a command string");
            }
            String command = ((String) value).trim();
            if (command.startsWith("/")) {
                command = command.substring(1).trim();
            }
            if (command.isEmpty() || command.startsWith("/") || command.indexOf('\n') >= 0
                    || command.indexOf('\r') >= 0) {
                throw pc.error("expected a single non-empty command");
            }
            final Template template = Template.compile(command, false);
            final boolean player = asPlayer;
            return ctx -> {
                final CommandSender sender = player ? ctx.player : Bukkit.getConsoleSender();
                final String line = template.render(ctx).trim();
                if (line.isEmpty() || !Bukkit.dispatchCommand(sender, line)) {
                    throw new IllegalStateException("Command was not found: " + line);
                }
                ctx.commandsDispatched++;
            };
        });

        engine.registerAction("sound", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            String key;
            double volume = 1.0;
            double pitch = 1.0;
            Who who = Who.SELF;
            if (map != null) {
                Cfg.checkKeys(map, pc, "sound", "volume", "pitch", "target");
                if (map.get("sound") == null) {
                    throw pc.error("missing 'sound'");
                }
                key = String.valueOf(map.get("sound"));
                volume = map.containsKey("volume") ? Cfg.number(map.get("volume"), pc.child("volume")) : 1.0;
                pitch = map.containsKey("pitch") ? Cfg.number(map.get("pitch"), pc.child("pitch")) : 1.0;
                who = who(map.get("target"), Who.SELF, pc.child("target"));
            } else {
                final String[] parts = String.valueOf(cfg).split(",");
                key = parts[0];
                if (parts.length > 1) {
                    volume = Cfg.number(parts[1], pc);
                }
                if (parts.length > 2) {
                    pitch = Cfg.number(parts[2], pc);
                }
            }
            key = key.trim().toLowerCase(Locale.ROOT);
            if (!SOUND_KEY.matcher(key).matches()) {
                throw pc.error("'" + key + "' is not a sound key; use the form entity.experience_orb.pickup");
            }
            final String sound = key;
            final float vol = (float) volume;
            final float pit = (float) pitch;
            final Who target = who;
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e != null) {
                    e.getWorld().playSound(e.getLocation(), sound, vol, pit);
                }
            };
        });

        engine.registerAction("potion", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map == null) {
                throw pc.error("expected 'effect', 'duration', 'amplifier' and optionally 'target'");
            }
            Cfg.checkKeys(map, pc, "effect", "duration", "amplifier", "target");
            if (map.get("effect") == null) {
                throw pc.error("missing 'effect'");
            }
            final PotionEffectType type = potionType(String.valueOf(map.get("effect")));
            if (type == null) {
                throw pc.child("effect").error("unknown potion effect '" + map.get("effect") + "'");
            }
            final int ticks = Math.max(1, TimeParser.parseTicks(map.getOrDefault("duration", "10s"), 1000, pc.child("duration")));
            final int amplifier = (int) Cfg.number(map.getOrDefault("amplifier", 0), pc.child("amplifier"));
            if (amplifier < 0 || amplifier > 255) {
                throw pc.child("amplifier").error("must be between 0 and 255");
            }
            final Who target = who(map.get("target"), Who.SELF, pc.child("target"));
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e instanceof LivingEntity) {
                    ((LivingEntity) e).addPotionEffect(new PotionEffect(type, ticks, amplifier));
                }
            };
        });

        engine.registerAction("damage", (cfg, pc) -> {
            final Object[] parsed = amountAndTarget(cfg, pc, Who.TARGET);
            final double amount = (Double) parsed[0];
            final Who target = (Who) parsed[1];
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e instanceof LivingEntity) {
                    ((LivingEntity) e).damage(amount); // no source: this must not trigger ATTACK rules again
                }
            };
        });

        engine.registerAction("heal", (cfg, pc) -> {
            final Object[] parsed = amountAndTarget(cfg, pc, Who.SELF);
            final double amount = (Double) parsed[0];
            final Who target = (Who) parsed[1];
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e instanceof LivingEntity) {
                    final LivingEntity living = (LivingEntity) e;
                    final Attribute health = at.dumphey.itemeditor.compatibility.VersionUtils.maxHealthAttribute();
                    final AttributeInstance max = health == null ? null : living.getAttribute(health);
                    final double limit = max == null ? 20.0 : max.getValue();
                    living.setHealth(Math.max(0, Math.min(limit, living.getHealth() + amount)));
                }
            };
        });

        engine.registerAction("feed", (cfg, pc) -> {
            final Object[] parsed = amountAndTarget(cfg, pc, Who.SELF);
            final int amount = (int) Math.round((Double) parsed[0]);
            final Who target = (Who) parsed[1];
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e instanceof Player) {
                    final Player p = (Player) e;
                    p.setFoodLevel(Math.max(0, Math.min(20, p.getFoodLevel() + amount)));
                }
            };
        });

        engine.registerAction("ignite", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            Object duration = cfg;
            Who who = Who.TARGET;
            if (map != null) {
                Cfg.checkKeys(map, pc, "duration", "target");
                duration = map.getOrDefault("duration", "3s");
                who = who(map.get("target"), Who.TARGET, pc.child("target"));
            }
            final int ticks = Math.max(1, TimeParser.parseTicks(duration, 1000, pc));
            final Who target = who;
            return ctx -> {
                final Entity e = target.resolve(ctx);
                if (e != null) {
                    e.setFireTicks(ticks);
                }
            };
        });

        engine.registerAction("set-damage", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map == null || map.size() != 1) {
                throw pc.error("expected exactly one of 'multiply', 'add' or 'set'");
            }
            Cfg.checkKeys(map, pc, "multiply", "add", "set");
            final String mode = map.keySet().iterator().next().toLowerCase(Locale.ROOT);
            final double value = Cfg.number(map.values().iterator().next(), pc.child(mode));
            return ctx -> {
                switch (mode) {
                    case "multiply":
                        ctx.setDamage(ctx.getDamage() * value);
                        break;
                    case "add":
                        ctx.setDamage(ctx.getDamage() + value);
                        break;
                    default:
                        ctx.setDamage(value);
                        break;
                }
            };
        });

        engine.registerAction("cancel", (cfg, pc) -> {
            if (!Cfg.bool(cfg, pc)) {
                return ctx -> { };
            }
            return TriggerContext::cancel;
        });

        engine.registerAction("set-cooldown", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            String key;
            Object duration;
            if (map != null) {
                Cfg.checkKeys(map, pc, "key", "duration");
                if (map.get("key") == null || map.get("duration") == null) {
                    throw pc.error("needs 'key' and 'duration'");
                }
                key = String.valueOf(map.get("key"));
                duration = map.get("duration");
            } else {
                final String[] parts = String.valueOf(cfg).split(",", 2);
                if (parts.length < 2) {
                    throw pc.error("expected \"key, duration\" (example: \"fire-burst, 10s\")");
                }
                key = parts[0];
                duration = parts[1].trim();
            }
            final String cooldownKey = key.trim();
            final long ms = TimeParser.parseMillis(duration, 1000, pc);
            final TriggerEngine eng = pc.engine();
            return ctx -> eng.cooldowns().set(ctx.player.getUniqueId(), cooldownKey, ms);
        });

        engine.registerAction("delay", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map == null) {
                throw pc.error("expected 'time' and 'actions'");
            }
            Cfg.checkKeys(map, pc, "time", "actions");
            if (map.get("time") == null || map.get("actions") == null) {
                throw pc.error("needs 'time' and 'actions'");
            }
            final int ticks = Math.max(1, TimeParser.parseTicks(map.get("time"), 1000, pc.child("time")));
            if (ticks > MAX_DELAY_TICKS) {
                throw pc.child("time").error("the longest delay is 1h");
            }
            final RuleAction inner = sequence(parseAll(map.get("actions"), pc.child("actions")));
            final TriggerEngine eng = pc.engine();
            return ctx -> eng.runLater(ticks, ctx.snapshot(), inner);
        });

        engine.registerAction("if", (cfg, pc) -> {
            final Map<String, Object> map = Cfg.map(cfg);
            if (map == null) {
                throw pc.error("expected 'conditions', 'then' and optionally 'else'");
            }
            Cfg.checkKeys(map, pc, "conditions", "then", "else");
            if (map.get("conditions") == null || map.get("then") == null) {
                throw pc.error("needs 'conditions' and 'then'");
            }
            final Condition condition = Conditions.parseAll(map.get("conditions"), pc.child("conditions"));
            final RuleAction then = sequence(parseAll(map.get("then"), pc.child("then")));
            final RuleAction otherwise = map.get("else") == null ? null : sequence(parseAll(map.get("else"), pc.child("else")));
            return ctx -> {
                if (condition == null || condition.test(ctx)) {
                    then.execute(ctx);
                } else if (otherwise != null) {
                    otherwise.execute(ctx);
                }
            };
        });
    }

    /** A list of {@code name: value} entries. */
    public static RuleAction[] parseAll(Object value, ParseContext pc) throws ConfigException {
        final List<Object> entries = Cfg.list(value);
        final List<RuleAction> list = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            final ParseContext ipc = pc.index(i);
            final Map<String, Object> map = Cfg.map(entries.get(i));
            if (map == null) {
                throw ipc.error("expected 'name: value' but found '" + entries.get(i) + "'");
            }
            for (Map.Entry<String, Object> e : map.entrySet()) {
                list.add(pc.engine().parseAction(e.getKey(), e.getValue(), ipc.child(e.getKey())));
            }
        }
        return list.toArray(new RuleAction[0]);
    }

    public static RuleAction sequence(final RuleAction[] actions) {
        if (actions.length == 1) {
            return actions[0];
        }
        return ctx -> {
            for (RuleAction a : actions) {
                a.execute(ctx);
            }
        };
    }

    private static Template displayText(Object value, ParseContext pc) throws ConfigException {
        if (!(value instanceof String)) {
            throw pc.error("expected text");
        }
        return Template.compile((String) value, true);
    }

    private static int titleTicks(Map<String, Object> map, String key, int fallback, ParseContext pc)
            throws ConfigException {
        if (map == null || !map.containsKey(key)) {
            return fallback;
        }
        final int ticks = TimeParser.parseTicks(map.get(key), 1000, pc.child(key));
        if (ticks > 1200) {
            throw pc.child(key).error("must not exceed 60s");
        }
        return ticks;
    }

    private static double effectNumber(Map<String, Object> map, String key, double fallback, double min,
                                       double max, ParseContext pc) throws ConfigException {
        final double value = map == null || !map.containsKey(key) ? fallback : Cfg.number(map.get(key), pc.child(key));
        if (!Double.isFinite(value) || value < min || value > max) {
            throw pc.child(key).error("expected a number from " + min + " to " + max);
        }
        return value;
    }

    private static Who who(Object value, Who def, ParseContext pc) throws ConfigException {
        if (value == null) {
            return def;
        }
        switch (String.valueOf(value).toLowerCase(Locale.ROOT)) {
            case "self":
            case "player":
                return Who.SELF;
            case "target":
            case "victim":
            case "other":
                return Who.TARGET;
            default:
                throw pc.error("expected 'self' or 'target' but found '" + value + "'");
        }
    }

    /** "5" or { amount: 5, target: self } -> [Double amount, Who target]. */
    private static Object[] amountAndTarget(Object cfg, ParseContext pc, Who def) throws ConfigException {
        final Map<String, Object> map = Cfg.map(cfg);
        if (map == null) {
            return new Object[]{Cfg.number(cfg, pc), def};
        }
        Cfg.checkKeys(map, pc, "amount", "target");
        if (map.get("amount") == null) {
            throw pc.error("missing 'amount'");
        }
        return new Object[]{Cfg.number(map.get("amount"), pc.child("amount")), who(map.get("target"), def, pc.child("target"))};
    }

    private static Particle particleByName(String name, ParseContext pc) throws ConfigException {
        final String constant = name.toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(constant);
        } catch (IllegalArgumentException ex) {
            final String alternative;
            switch (constant) {
                case "HAPPY_VILLAGER": alternative = "VILLAGER_HAPPY"; break;
                case "VILLAGER_HAPPY": alternative = "HAPPY_VILLAGER"; break;
                case "ANGRY_VILLAGER": alternative = "VILLAGER_ANGRY"; break;
                case "VILLAGER_ANGRY": alternative = "ANGRY_VILLAGER"; break;
                case "EXPLOSION": alternative = "EXPLOSION_LARGE"; break;
                case "EXPLOSION_LARGE": alternative = "EXPLOSION"; break;
                default: throw pc.error("unknown particle '" + name + "'");
            }
            try {
                return Particle.valueOf(alternative);
            } catch (IllegalArgumentException ignored) {
                throw pc.error("unknown particle '" + name + "'");
            }
        }
    }

    private static PotionEffectType potionType(String name) {
        final String lower = name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(lower));
        if (type == null) {
            type = PotionEffectType.getByName(lower.toUpperCase(Locale.ROOT));
        }
        return type;
    }
}
