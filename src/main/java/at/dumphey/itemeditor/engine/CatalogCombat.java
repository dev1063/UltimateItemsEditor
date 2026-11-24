package at.dumphey.itemeditor.engine;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

final class CatalogCombat implements Listener {
    private static final int MAX_AREA_TARGETS = 8;
    private static final int MAX_BLEEDS = 64;
    private static final int EXPLOSIVE = 1;
    private static final int POISON = 2;
    private static final int BLIND = 4;
    private static final int WITHER = 8;
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    private final TriggerEngine engine;
    private final CatalogEffects effects;
    private final NamespacedKey arrowKey;
    private final NamespacedKey arrowExpiry;
    private boolean listening;
    private boolean secondaryDamage;
    private int pendingBleeds;
    private long generation;

    CatalogCombat(TriggerEngine engine, CatalogEffects effects) {
        this.engine = engine;
        this.effects = effects;
        arrowKey = new NamespacedKey(engine.plugin(), "catalog_arrow_effects");
        arrowExpiry = new NamespacedKey(engine.plugin(), "catalog_arrow_expiry");
        engine.registerAction("catalog-combat", (cfg, pc) -> {
            final Effect effect;
            try {
                effect = Effect.valueOf(String.valueOf(cfg).toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ex) {
                throw pc.error("unknown catalog combat effect: " + cfg);
            }
            return ctx -> {
                if (!secondaryDamage) apply(effect, ctx);
            };
        });
        engine.registerCondition("catalog-test", (cfg, pc) -> {
            switch (String.valueOf(cfg)) {
                case "fall": return ctx -> cause(ctx, EntityDamageEvent.DamageCause.FALL);
                case "projectile": return ctx -> cause(ctx, EntityDamageEvent.DamageCause.PROJECTILE);
                case "enemy-sword": return CatalogCombat::enemySword;
                case "critical": return ctx -> ctx.target instanceof LivingEntity
                        && ((LivingEntity) ctx.target).getHealth() < 5.0;
                case "lethal": return ctx -> ctx.event instanceof EntityDamageEvent
                        && ((EntityDamageEvent) ctx.event).getFinalDamage() >= ctx.player.getHealth();
                case "near-death": return ctx -> ctx.event instanceof EntityDamageEvent
                        && ctx.player.getHealth() - ((EntityDamageEvent) ctx.event).getFinalDamage() <= 6.0;
                case "burning": return ctx -> ctx.player.getFireTicks() > 0;
                case "living-target": return ctx -> ctx.target instanceof LivingEntity;
                case "night-mob": return ctx -> ctx.target instanceof Mob
                        && ctx.player.getWorld().getTime() >= 13000 && ctx.player.getWorld().getTime() < 23000;
                case "end-mob": return ctx -> ctx.target != null
                        && (ctx.target.getType() == org.bukkit.entity.EntityType.ENDERMAN
                        || ctx.target.getType() == org.bukkit.entity.EntityType.ENDER_DRAGON);
                case "rocket": return ctx -> ctx.event instanceof PlayerInteractEvent
                        && ((PlayerInteractEvent) ctx.event).getItem() != null
                        && ((PlayerInteractEvent) ctx.event).getItem().getType() == Material.FIREWORK_ROCKET;
                case "no-rocket": return ctx -> ctx.event instanceof PlayerInteractEvent
                        && (((PlayerInteractEvent) ctx.event).getItem() == null
                        || ((PlayerInteractEvent) ctx.event).getItem().getType() != Material.FIREWORK_ROCKET);
                default: throw pc.error("unknown catalog condition: " + cfg);
            }
        });
    }

    private enum Effect {
        EXPLOSIVE, POISON_ARROW, BLIND_ARROW, WITHER_ARROW, FREEZE, THUNDER, DISORDER, DISARM,
        REFORGE, LASTING, BINDING, BEHEAD, PULL, MOB_NOVA, PLAYER_NOVA, SWORD_WARD,
        DODGE_ARROW, SACRIFICE, BLEED, PLANT, LAUNCH, CLEANSE, CREEPER, EXTINGUISH,
        ROCKET, REFLECT, REVIVE, GLIDE, EXECUTE, FISH_POISON, FISH_FIRE
    }

    private void apply(Effect effect, TriggerContext ctx) {
        final Player player = ctx.player;
        final LivingEntity target = ctx.target instanceof LivingEntity ? (LivingEntity) ctx.target : null;
        final int level = Math.max(1, Math.min(4, ctx.level));
        switch (effect) {
            case EXPLOSIVE: mark(ctx, EXPLOSIVE); break;
            case POISON_ARROW: mark(ctx, POISON); break;
            case BLIND_ARROW: mark(ctx, BLIND); break;
            case WITHER_ARROW: mark(ctx, WITHER); break;
            case FISH_POISON:
            case FISH_FIRE:
                if (target instanceof Player && ctx.event instanceof org.bukkit.event.player.PlayerFishEvent) {
                    final org.bukkit.event.player.PlayerFishEvent event =
                            (org.bukkit.event.player.PlayerFishEvent) ctx.event;
                    later(1, () -> {
                        if (!event.isCancelled() && player.isOnline() && target.isValid()
                                && target.getWorld() == player.getWorld()) {
                            if (effect == Effect.FISH_POISON) potion(target, PotionEffectType.POISON, 60 + level * 20);
                            else target.setFireTicks(Math.max(target.getFireTicks(), 30 + 20 * level));
                        }
                    });
                }
                break;
            case FREEZE:
                if (target != null) {
                    target.setFreezeTicks(Math.min(target.getMaxFreezeTicks(), target.getFreezeTicks() + 55 + level * 20));
                    potion(target, PotionEffectType.SLOWNESS, 35 + level * 15);
                }
                break;
            case THUNDER:
                if (target != null) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    ctx.setDamage(ctx.getDamage() + level);
                }
                break;
            case DISORDER:
                if (target instanceof Player) shuffle((Player) target);
                break;
            case DISARM:
                if (target instanceof Player) disarm((Player) target);
                break;
            case REFORGE:
                if (target instanceof Player) repairHand(player, 10 * level);
                break;
            case LASTING:
                if (ctx.event instanceof PlayerItemDamageEvent) {
                    final PlayerItemDamageEvent event = (PlayerItemDamageEvent) ctx.event;
                    final ItemMeta meta = event.getItem().getItemMeta();
                    if (meta instanceof Damageable && ((Damageable) meta).getDamage() + event.getDamage()
                            >= maxDamage(event.getItem(), (Damageable) meta) && repair(event.getItem(), 25 * level)) {
                        event.setDamage(0);
                    }
                }
                break;
            case BINDING: bind(ctx); break;
            case BEHEAD: behead(ctx); break;
            case PULL:
                if (target instanceof Mob) push(target, player.getLocation().toVector()
                        .subtract(target.getLocation().toVector()), 0.45 + level * 0.13, 0.18);
                break;
            case MOB_NOVA:
            case PLAYER_NOVA:
                if (target != null) area(player, target, target.getLocation(), 1.5 + level * 0.65,
                        1.0 + level * 0.65, effect == Effect.PLAYER_NOVA);
                break;
            case SWORD_WARD:
                if (enemySword(ctx)) ctx.setDamage(ctx.getDamage() * (1 - 0.02 * level));
                break;
            case DODGE_ARROW:
                if (ctx.event instanceof EntityDamageByEntityEvent
                        && ((EntityDamageByEntityEvent) ctx.event).getDamager() instanceof AbstractArrow) ctx.cancel();
                break;
            case SACRIFICE:
                if (repairHand(player, 2 * level)) ctx.setDamage(ctx.getDamage() * (1 - level * 0.05));
                break;
            case BLEED:
                if (target != null) bleed(player, target, level);
                break;
            case PLANT: plant(ctx); break;
            case LAUNCH:
                if (target != null) target.setVelocity(new Vector(0, 0.45 + level * 0.12, 0));
                break;
            case CLEANSE:
                for (PotionEffect active : player.getActivePotionEffects()) {
                    if (negative(active.getType())) player.removePotionEffect(active.getType());
                }
                break;
            case CREEPER:
                if (target instanceof Mob) effects.guard(player, target);
                break;
            case EXTINGUISH: player.setFireTicks(0); break;
            case ROCKET: boost(ctx, 0.25 + 0.12 * level, false); break;
            case GLIDE: boost(ctx, 0.4 + 0.15 * level, true); break;
            case REFLECT:
                if (target != null && target != player && ctx.getDamage() > 0) {
                    final double reflected = Math.min(16, ctx.getDamage() * (1 + level * 0.18));
                    ctx.cancel();
                    hit(player, target, reflected);
                }
                break;
            case REVIVE:
                if (ctx.event instanceof EntityDamageEvent
                        && ((EntityDamageEvent) ctx.event).getFinalDamage() >= player.getHealth()) {
                    ctx.cancel();
                    player.setHealth(Math.min(player.getMaxHealth(), 4.0 + level * 2));
                    player.setFireTicks(0);
                    player.setFallDistance(0);
                    player.setNoDamageTicks(30);
                }
                break;
            case EXECUTE:
                if (target != null && target.getHealth() < 5.0 && ctx.event instanceof EntityDamageEvent) {
                    final EntityDamageEvent event = (EntityDamageEvent) ctx.event;
                    final double finalDamage = event.getFinalDamage();
                    if (finalDamage > 0) {
                        final double required = ctx.getDamage() * (target.getHealth() + target.getAbsorptionAmount() + 1) / finalDamage;
                        ctx.setDamage(Math.max(ctx.getDamage(), Math.min(128, required)));
                    }
                }
                break;
            default: break;
        }
    }

    private static boolean negative(PotionEffectType type) {
        return type.equals(PotionEffectType.POISON) || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.SLOWNESS) || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.BLINDNESS) || type.equals(PotionEffectType.NAUSEA)
                || type.equals(PotionEffectType.HUNGER) || type.equals(PotionEffectType.MINING_FATIGUE)
                || type.equals(PotionEffectType.DARKNESS) || type.equals(PotionEffectType.LEVITATION)
                || type.equals(PotionEffectType.BAD_OMEN);
    }

    private static boolean cause(TriggerContext ctx, EntityDamageEvent.DamageCause cause) {
        return ctx.event instanceof EntityDamageEvent && ((EntityDamageEvent) ctx.event).getCause() == cause;
    }

    private static boolean enemySword(TriggerContext ctx) {
        if (!(ctx.event instanceof EntityDamageByEntityEvent)) return false;
        final Entity attacker = ((EntityDamageByEntityEvent) ctx.event).getDamager();
        if (!(attacker instanceof LivingEntity)) return false;
        final org.bukkit.inventory.EntityEquipment equipment = ((LivingEntity) attacker).getEquipment();
        return equipment != null && equipment.getItemInMainHand().getType().name().endsWith("_SWORD");
    }

    private void mark(TriggerContext ctx, int bit) {
        if (!(ctx.event instanceof EntityShootBowEvent)) return;
        final Entity projectile = ((EntityShootBowEvent) ctx.event).getProjectile();
        if (!(projectile instanceof Arrow)) return;
        if (!listening) {
            Bukkit.getPluginManager().registerEvents(this, engine.plugin());
            listening = true;
        }
        final PersistentDataContainer data = projectile.getPersistentDataContainer();
        data.set(arrowKey, PersistentDataType.INTEGER, data.getOrDefault(arrowKey, PersistentDataType.INTEGER, 0) | bit);
        data.set(arrowExpiry, PersistentDataType.LONG, System.currentTimeMillis() + 20_000);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow) || !(event.getEntity() instanceof LivingEntity)
                || event.getFinalDamage() <= 0) return;
        final Arrow arrow = (Arrow) event.getDamager();
        final int mask = arrowMask(arrow);
        if ((mask & (POISON | BLIND | WITHER)) == 0) return;
        final LivingEntity victim = (LivingEntity) event.getEntity();
        if ((mask & POISON) != 0) potion(victim, PotionEffectType.POISON, 65);
        if ((mask & BLIND) != 0) potion(victim, PotionEffectType.BLINDNESS, 45);
        if ((mask & WITHER) != 0) potion(victim, PotionEffectType.WITHER, 60);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow) || !(event.getEntity().getShooter() instanceof Player)) return;
        final Arrow arrow = (Arrow) event.getEntity();
        if ((arrowMask(arrow) & EXPLOSIVE) == 0) return;
        final PersistentDataContainer data = arrow.getPersistentDataContainer();
        data.set(arrowKey, PersistentDataType.INTEGER, arrowMask(arrow) & ~EXPLOSIVE);
        final Player player = (Player) arrow.getShooter();
        final Location location = arrow.getLocation();
        final Entity direct = event.getHitEntity();
        later(1, () -> {
            if (!player.isOnline() || player.getWorld() != location.getWorld()
                    || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
            location.getWorld().spawnParticle(Particle.EXPLOSION, location, 1);
            location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);
            area(player, direct, location, 2.8, 4.0, null);
            if (direct instanceof LivingEntity && direct != player) hit(player, (LivingEntity) direct, 3.0);
        });
    }

    private int arrowMask(Arrow arrow) {
        final PersistentDataContainer data = arrow.getPersistentDataContainer();
        final Integer mask = data.get(arrowKey, PersistentDataType.INTEGER);
        if (mask == null) return 0;
        final long expiry = data.getOrDefault(arrowExpiry, PersistentDataType.LONG, 0L);
        return expiry >= System.currentTimeMillis() ? mask : 0;
    }

    private static void potion(LivingEntity entity, PotionEffectType type, int ticks) {
        entity.addPotionEffect(new PotionEffect(type, ticks, 0));
    }

    private void area(Player player, Entity direct, Location center, double radius, double damage, Boolean players) {
        int remaining = MAX_AREA_TARGETS;
        final double square = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity == player || entity == direct || !(entity instanceof LivingEntity)) continue;
            if (players != null && (players ? !(entity instanceof Player) : !(entity instanceof Mob))) continue;
            if (entity.getLocation().distanceSquared(center) > square) continue;
            hit(player, (LivingEntity) entity, damage);
            if (--remaining == 0) return;
        }
    }

    private void hit(Player owner, LivingEntity target, double amount) {
        if (secondaryDamage || !target.isValid() || owner.getWorld() != target.getWorld()) return;
        secondaryDamage = true;
        try {
            target.damage(amount, owner);
        } finally {
            secondaryDamage = false;
        }
    }

    private void bleed(Player player, LivingEntity target, int level) {
        if (pendingBleeds >= MAX_BLEEDS) return;
        pendingBleeds++;
        for (int pulse = 1; pulse <= 3; pulse++) {
            final boolean last = pulse == 3;
            later(pulse * 20, () -> {
                if (last) pendingBleeds--;
                if (player.isOnline() && target.isValid() && player.getWorld() == target.getWorld()) {
                    target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, 0.8, 0), 2);
                    hit(player, target, 0.7 + 0.4 * level);
                }
            });
        }
    }

    private static void shuffle(Player target) {
        final PlayerInventory inventory = target.getInventory();
        for (int i = 8; i > 0; i--) {
            final int other = ThreadLocalRandom.current().nextInt(i + 1);
            if (i == other) continue;
            final ItemStack item = inventory.getItem(i);
            inventory.setItem(i, inventory.getItem(other));
            inventory.setItem(other, item);
        }
    }

    private static void disarm(Player target) {
        final PlayerInventory inventory = target.getInventory();
        final int free = inventory.firstEmpty();
        if (free < 0 || free >= 36) return;
        final int first = ThreadLocalRandom.current().nextInt(ARMOR.length);
        for (int i = 0; i < ARMOR.length; i++) {
            final EquipmentSlot slot = ARMOR[(first + i) % ARMOR.length];
            final ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir() || item.containsEnchantment(org.bukkit.enchantments.Enchantment.BINDING_CURSE)) continue;
            inventory.setItem(slot, null);
            inventory.setItem(free, item);
            return;
        }
    }

    private static boolean repairHand(Player player, int amount) {
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (!repair(item, amount)) return false;
        player.getInventory().setItemInMainHand(item);
        return true;
    }

    private static boolean repair(ItemStack item, int amount) {
        final ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable) || ((Damageable) meta).getDamage() <= 0) return false;
        final Damageable damage = (Damageable) meta;
        damage.setDamage(Math.max(0, damage.getDamage() - amount));
        return item.setItemMeta(meta);
    }

    private static int maxDamage(ItemStack item, Damageable damage) {
        return damage.hasMaxDamage() ? damage.getMaxDamage() : item.getType().getMaxDurability();
    }

    private static void bind(TriggerContext ctx) {
        if (!(ctx.event instanceof PlayerDeathEvent) || ctx.item == null) return;
        final PlayerDeathEvent death = (PlayerDeathEvent) ctx.event;
        if (death.getKeepInventory()) {
            death.getEntity().getInventory().setItem(ctx.slot, null);
        } else {
            for (java.util.Iterator<ItemStack> it = death.getDrops().iterator(); it.hasNext();) {
                if (it.next().equals(ctx.item)) {
                    it.remove();
                    break;
                }
            }
        }
    }

    private static void behead(TriggerContext ctx) {
        if (!(ctx.event instanceof EntityDeathEvent) || !(ctx.target instanceof LivingEntity)) return;
        final Material material;
        switch (ctx.target.getType()) {
            case PLAYER: material = Material.PLAYER_HEAD; break;
            case SKELETON: material = Material.SKELETON_SKULL; break;
            case WITHER_SKELETON: material = Material.WITHER_SKELETON_SKULL; break;
            case ZOMBIE: material = Material.ZOMBIE_HEAD; break;
            case CREEPER: material = Material.CREEPER_HEAD; break;
            case PIGLIN: material = Material.PIGLIN_HEAD; break;
            default: return;
        }
        final ItemStack head = new ItemStack(material);
        if (ctx.target instanceof Player) {
            final SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer((Player) ctx.target);
            head.setItemMeta(meta);
        }
        ((EntityDeathEvent) ctx.event).getDrops().add(head);
    }

    private void boost(TriggerContext ctx, double speed, boolean cancel) {
        if (!(ctx.event instanceof PlayerInteractEvent) || !ctx.player.isGliding()) return;
        final PlayerInteractEvent event = (PlayerInteractEvent) ctx.event;
        final Player player = ctx.player;
        if (cancel) ctx.cancel();
        later(1, () -> {
            if ((!cancel && TriggerContext.isInteractionCancelled(event)) || !player.isOnline() || !player.isGliding()) return;
            final Vector current = player.getVelocity();
            final Vector next = current.add(player.getLocation().getDirection().multiply(speed));
            if (next.lengthSquared() > 3.24) next.normalize().multiply(1.8);
            player.setVelocity(next);
        });
    }

    private static void push(LivingEntity entity, Vector direction, double speed, double vertical) {
        if (direction.lengthSquared() < 0.01) return;
        entity.setVelocity(direction.normalize().multiply(speed).setY(vertical));
    }

    private void plant(TriggerContext ctx) {
        if (!(ctx.event instanceof PlayerInteractEvent) || !ctx.player.isSneaking() || ctx.click != ClickKind.RIGHT_BLOCK) return;
        final PlayerInteractEvent interaction = (PlayerInteractEvent) ctx.event;
        if (ctx.block == null || ctx.block.getType() != Material.FARMLAND || interaction.getBlockFace() != BlockFace.UP) return;
        final Block root = ctx.block;
        final Player player = ctx.player;
        final EquipmentSlot hand = ctx.slot;
        ctx.cancel();
        later(1, () -> plantArea(root, player, hand));
    }

    private void plantArea(Block root, Player player, EquipmentSlot slot) {
        if (!player.isOnline() || player.getWorld() != root.getWorld() || player.getGameMode() == GameMode.ADVENTURE
                || player.getGameMode() == GameMode.SPECTATOR || player.getLocation().distanceSquared(root.getLocation()) > 64) return;
        final ItemStack hoe = player.getInventory().getItem(slot);
        if (hoe == null || !hoe.getType().name().endsWith("_HOE")) return;
        final BlockData carrots = Material.CARROTS.createBlockData();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!root.getWorld().isChunkLoaded((root.getX() + dx) >> 4, (root.getZ() + dz) >> 4)) continue;
                final Block soil = root.getRelative(dx, 0, dz);
                final Block crop = soil.getRelative(BlockFace.UP);
                if (soil.getType() != Material.FARMLAND || !crop.getType().isAir()) continue;
                final int seedSlot = player.getInventory().first(Material.CARROT);
                if (seedSlot < 0 && player.getGameMode() != GameMode.CREATIVE) return;
                final ItemStack seeds = seedSlot < 0 ? new ItemStack(Material.CARROT) : player.getInventory().getItem(seedSlot);
                final BlockState before = crop.getState();
                crop.setBlockData(carrots, false);
                final BlockPlaceEvent placed = new BlockPlaceEvent(crop, before, soil, seeds, player, true, slot);
                Bukkit.getPluginManager().callEvent(placed);
                if (placed.isCancelled() || !placed.canBuild() || crop.getType() != Material.CARROTS) {
                    if (crop.getType() == Material.CARROTS) before.update(true, false);
                } else if (player.getGameMode() != GameMode.CREATIVE) {
                    seeds.setAmount(seeds.getAmount() - 1);
                    player.getInventory().setItem(seedSlot, seeds.getAmount() > 0 ? seeds : null);
                }
            }
        }
    }

    private void later(int ticks, Runnable task) {
        final long epoch = generation;
        Bukkit.getScheduler().runTaskLater(engine.plugin(), () -> {
            if (generation == epoch) task.run();
        }, ticks);
    }

    boolean isSecondaryDamage() {
        return secondaryDamage;
    }

    void stop() {
        generation++;
        pendingBleeds = 0;
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
    }
}
