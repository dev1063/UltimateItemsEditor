package at.dumphey.itemeditor.engine;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Recipe;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

final class CatalogEffects implements Listener {
    private static final int MAX_SUMMONS = 48;
    private static final int MAX_MAGMA = 128;
    private final TriggerEngine engine;
    private final CatalogWorkQueue work;
    private final NamespacedKey warp;
    private final Map<UUID, Summon> summons = new HashMap<>();
    private final Map<Block, BlockState> magma = new HashMap<>();
    private final Map<Chunk, Integer> tickets = new HashMap<>();
    private final Map<Block, DropPlan> collections = new HashMap<>();
    private Map<Material, ItemStack> cooked;
    private BukkitTask collectionCleanup;
    private boolean listening;
    private long generation;

    CatalogEffects(TriggerEngine engine, CatalogWorkQueue work) {
        this.engine = engine;
        this.work = work;
        this.warp = new NamespacedKey(engine.plugin(), "catalog_warp_arrow");

        engine.registerAction("catalog-exp", (cfg, pc) -> {
            final double factor = Cfg.number(cfg, pc);
            if (!Double.isFinite(factor) || factor < 1 || factor > 4) {
                throw pc.error("experience multiplier must be 1–4");
            }
            return ctx -> {
                if (ctx.event instanceof BlockBreakEvent) {
                    final BlockBreakEvent event = (BlockBreakEvent) ctx.event;
                    event.setExpToDrop(scaleExperience(event.getExpToDrop(), factor));
                } else if (ctx.event instanceof EntityDeathEvent) {
                    final EntityDeathEvent event = (EntityDeathEvent) ctx.event;
                    event.setDroppedExp(scaleExperience(event.getDroppedExp(), factor));
                }
            };
        });
        engine.registerAction("catalog-glass", (cfg, pc) -> ctx -> {
            if (ctx.event instanceof BlockDamageEvent && isGlass(ctx.block.getType())) {
                ((BlockDamageEvent) ctx.event).setInstaBreak(true);
            }
        });
        engine.registerAction("catalog-collect", (cfg, pc) -> ctx -> planDrops(ctx, false));
        engine.registerAction("catalog-cook", (cfg, pc) -> ctx -> planDrops(ctx, true));
        engine.registerAction("catalog-volley", (cfg, pc) -> this::volley);
        engine.registerAction("catalog-warp", (cfg, pc) -> this::markProjectile);
        engine.registerAction("catalog-magma", (cfg, pc) -> this::magmaStep);
        engine.registerAction("catalog-summon", (cfg, pc) -> {
            final boolean golem = "golem".equals(cfg);
            if (!golem && !"undead".equals(cfg)) {
                throw pc.error("expected undead or golem");
            }
            return ctx -> {
                if (ctx.target instanceof Mob && summons.size() < MAX_SUMMONS
                        && !summons.containsKey(ctx.target.getUniqueId()) && ctx.event instanceof Cancellable) {
                    final LivingEntity enemy = (LivingEntity) ctx.target;
                    final Player owner = ctx.player;
                    final Cancellable attack = (Cancellable) ctx.event;
                    work.offer(CatalogWorkQueue.SPAWN, () -> {
                        if (!attack.isCancelled()) summon(owner, enemy, golem);
                    });
                }
            };
        });
    }

    private void planDrops(TriggerContext ctx, boolean cook) {
        if (!(ctx.event instanceof BlockBreakEvent)) return;
        listen();
        final BlockBreakEvent event = (BlockBreakEvent) ctx.event;
        final DropPlan plan = collections.compute(ctx.block, (block, current) ->
                current != null && current.original == event ? current : new DropPlan(event));
        if (cook) plan.cook = true;
        else plan.collect = true;
        if (collectionCleanup == null) {
            collectionCleanup = later(1, () -> {
                collections.clear();
                collectionCleanup = null;
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDrops(BlockDropItemEvent event) {
        if (collections.isEmpty()) return;
        final DropPlan plan = collections.remove(event.getBlock());
        if (plan == null || plan.original.isCancelled() || event.isCancelled()
                || plan.original.getPlayer() != event.getPlayer()) return;
        if (plan.cook && cooked == null) loadRecipes();
        final Iterator<Item> iterator = event.getItems().iterator();
        while (iterator.hasNext()) {
            final Item drop = iterator.next();
            ItemStack stack = drop.getItemStack();
            final ItemStack result = plan.cook && !stack.hasItemMeta() ? cooked.get(stack.getType()) : null;
            if (result != null && stack.getAmount() * (long) result.getAmount() <= result.getMaxStackSize()) {
                final int count = stack.getAmount() * result.getAmount();
                stack = result.clone();
                stack.setAmount(count);
                drop.setItemStack(stack);
            }
            if (plan.collect) {
                final Map<Integer, ItemStack> rest = event.getPlayer().getInventory().addItem(stack.clone());
                if (rest.isEmpty()) iterator.remove();
                else drop.setItemStack(rest.values().iterator().next());
            }
        }
    }

    private void loadRecipes() {
        cooked = new EnumMap<>(Material.class);
        final Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            final Recipe recipe = recipes.next();
            if (!(recipe instanceof FurnaceRecipe)) continue;
            final FurnaceRecipe furnace = (FurnaceRecipe) recipe;
            if (furnace.getInputChoice() instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice) {
                final org.bukkit.inventory.RecipeChoice.MaterialChoice choice =
                        (org.bukkit.inventory.RecipeChoice.MaterialChoice) furnace.getInputChoice();
                for (Material input : choice.getChoices()) cooked.putIfAbsent(input, furnace.getResult());
            }
        }
    }

    private static final class DropPlan {
        final BlockBreakEvent original;
        boolean cook;
        boolean collect;

        DropPlan(BlockBreakEvent original) {
            this.original = original;
        }
    }

    private static int scaleExperience(int experience, double multiplier) {
        final double scaled = Math.min(Integer.MAX_VALUE, experience * multiplier);
        final int whole = (int) scaled;
        final double fraction = scaled - whole;
        return fraction > 0 && ThreadLocalRandom.current().nextDouble() < fraction ? whole + 1 : whole;
    }

    private static boolean isGlass(Material material) {
        final String name = material.name();
        return name.equals("GLASS") || name.equals("GLASS_PANE") || name.equals("TINTED_GLASS")
                || name.endsWith("_STAINED_GLASS") || name.endsWith("_STAINED_GLASS_PANE");
    }

    private Projectile projectile(TriggerContext ctx) {
        if (ctx.event instanceof EntityShootBowEvent) {
            final org.bukkit.entity.Entity shot = ((EntityShootBowEvent) ctx.event).getProjectile();
            return shot instanceof AbstractArrow ? (Projectile) shot : null;
        }
        if (ctx.event instanceof ProjectileLaunchEvent) {
            return ((ProjectileLaunchEvent) ctx.event).getEntity();
        }
        return null;
    }

    private void volley(TriggerContext ctx) {
        final Projectile shot = projectile(ctx);
        if (shot == null) return;
        final int arrows = Math.min(4, ctx.level);
        final Player player = ctx.player;
        final Cancellable event = (Cancellable) ctx.event;
        final Vector velocity = shot.getVelocity();
        final Location origin = shot.getLocation();
        final double speed = Math.min(3.5, velocity.length());
        if (speed < 0.1) return;
        final double damage = shot instanceof Arrow ? Math.min(((Arrow) shot).getDamage(), 4.0) : 2.0;
        final Vector normalized = velocity.multiply(speed / velocity.length());
        work.offer(CatalogWorkQueue.SPAWN, () -> {
            if (event.isCancelled() || !player.isOnline() || player.getWorld() != origin.getWorld()
                    || !origin.getWorld().isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) return;
            for (int i = 0; i < arrows; i++) {
                final double angle = Math.toRadians((i % 2 == 0 ? 1 : -1) * (5 + 5 * (i / 2)));
                final Vector direction = normalized.clone().rotateAroundY(angle);
                origin.getWorld().spawn(origin, Arrow.class, arrow -> {
                    arrow.setShooter(player);
                    arrow.setVelocity(direction);
                    arrow.setDamage(damage);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                });
            }
        });
    }

    private void markProjectile(TriggerContext ctx) {
        final Projectile shot = projectile(ctx);
        if (!(shot instanceof Arrow)) return;
        listen();
        shot.getPersistentDataContainer().set(warp, PersistentDataType.LONG, System.currentTimeMillis() + 15_000);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        final Projectile shot = event.getEntity();
        final Long expiry = shot.getPersistentDataContainer().get(warp, PersistentDataType.LONG);
        if (expiry == null) return;
        shot.getPersistentDataContainer().remove(warp);
        if (expiry < System.currentTimeMillis() || !(shot.getShooter() instanceof Player)) return;
        final Player player = (Player) shot.getShooter();
        final Location impact = event.getHitBlock() != null && event.getHitBlockFace() != null
                ? event.getHitBlock().getRelative(event.getHitBlockFace()).getLocation().add(0.5, 0, 0.5)
                : shot.getLocation();
        later(1, () -> warp(player, impact));
    }

    private void warp(Player player, Location impact) {
        final World world = impact.getWorld();
        if (!player.isOnline() || player.isDead() || player.getWorld() != world
                || !world.isChunkLoaded(impact.getBlockX() >> 4, impact.getBlockZ() >> 4)
                || !world.getWorldBorder().isInside(impact)) return;
        for (int dy = 0; dy <= 2; dy++) {
            final Location target = impact.clone().add(0, dy, 0);
            final int y = target.getBlockY();
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
            final Block feet = target.getBlock();
            final Block head = feet.getRelative(0, 1, 0);
            final Block ground = feet.getRelative(0, -1, 0);
            if (feet.getType().isAir() && head.getType().isAir() && ground.getType().isSolid()
                    && ground.getType() != Material.MAGMA_BLOCK && ground.getType() != Material.CACTUS) {
                final Location facing = player.getLocation();
                target.setYaw(facing.getYaw());
                target.setPitch(facing.getPitch());
                if (player.teleport(target, PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
                    player.setFallDistance(0);
                }
                return;
            }
        }
    }

    void guard(Player owner, LivingEntity enemy, Cancellable attack) {
        work.offer(CatalogWorkQueue.SPAWN, () -> {
            if (!attack.isCancelled()) summon(owner, enemy, EntityType.CREEPER);
        });
    }

    private void summon(Player owner, LivingEntity enemy, boolean golem) {
        summon(owner, enemy, golem ? EntityType.IRON_GOLEM
                : ThreadLocalRandom.current().nextBoolean() ? EntityType.SKELETON : EntityType.ZOMBIE);
    }

    private void summon(Player owner, LivingEntity enemy, EntityType type) {
        if (summons.size() >= MAX_SUMMONS || !owner.isOnline() || owner.isDead() || !enemy.isValid()
                || owner.getWorld() != enemy.getWorld()) return;
        final UUID ownerId = owner.getUniqueId();
        for (Summon active : summons.values()) {
            if (active.owner.equals(ownerId) && (active.mob.getType() == type
                    || type != EntityType.IRON_GOLEM && type != EntityType.CREEPER
                    && active.mob.getType() != EntityType.IRON_GOLEM && active.mob.getType() != EntityType.CREEPER)) return;
        }
        final Location location = owner.getLocation();
        listen();
        final Mob mob = (Mob) owner.getWorld().spawnEntity(location, type);
        if (!mob.isValid()) return;
        if (mob instanceof org.bukkit.entity.Creeper) {
            final org.bukkit.entity.Creeper creeper = (org.bukkit.entity.Creeper) mob;
            creeper.setExplosionRadius(0);
            creeper.setMaxFuseTicks(30);
        }
        mob.setPersistent(false);
        mob.setCanPickupItems(false);
        final UUID mobId = mob.getUniqueId();
        final BukkitTask timer = later(200, () -> {
            summons.remove(mobId);
            mob.remove();
        });
        summons.put(mobId, new Summon(ownerId, enemy.getUniqueId(), mob, timer));
        mob.setTarget(enemy);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        final Summon summon = summons.get(event.getEntity().getUniqueId());
        if (summon != null && event.getTarget() != null && !summon.enemy.equals(event.getTarget().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGuardExplosion(ExplosionPrimeEvent event) {
        final UUID id = event.getEntity().getUniqueId();
        final Summon guard = summons.get(id);
        if (guard == null || event.getEntityType() != EntityType.CREEPER) return;
        final boolean allowed = !event.isCancelled();
        event.setCancelled(true);
        summons.remove(id);
        guard.timer.cancel();
        final Location center = guard.mob.getLocation();
        final org.bukkit.entity.Entity target = Bukkit.getEntity(guard.enemy);
        final Player owner = Bukkit.getPlayer(guard.owner);
        guard.mob.remove();
        if (allowed && owner != null && target instanceof Mob && target.isValid()
                && target.getWorld() == center.getWorld() && target.getLocation().distanceSquared(center) <= 12.25) {
            center.getWorld().spawnParticle(Particle.EXPLOSION, center, 1);
            center.getWorld().playSound(center, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.1f);
            ((Mob) target).damage(6, owner);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(EntityDeathEvent event) {
        final Summon summon = summons.remove(event.getEntity().getUniqueId());
        if (summon != null) {
            summon.timer.cancel();
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTemporaryBlockBreak(BlockBreakEvent event) {
        if (!magma.isEmpty() && magma.containsKey(event.getBlock())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        if (!magma.isEmpty()) event.blockList().removeIf(magma::containsKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        if (!magma.isEmpty()) event.blockList().removeIf(magma::containsKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!magma.isEmpty() && event.getBlocks().stream().anyMatch(magma::containsKey)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!magma.isEmpty() && event.getBlocks().stream().anyMatch(magma::containsKey)) event.setCancelled(true);
    }

    private void magmaStep(TriggerContext ctx) {
        if (magma.size() >= MAX_MAGMA) return;
        final Player player = ctx.player;
        final World world = player.getWorld();
        final int radius = Math.min(2, Math.max(1, ctx.level));
        work.offer(CatalogWorkQueue.MAGMA, () -> {
            if (player.isOnline() && !player.isDead() && player.getWorld() == world) {
                magmaStep(player, radius);
            }
        });
    }

    private void magmaStep(Player player, int radius) {
        if (magma.size() >= MAX_MAGMA) return;
        final Block feet = player.getLocation().getBlock();
        if (!player.isOnGround() && feet.getType() != Material.LAVA) return;
        final Block below = feet.getType() == Material.LAVA ? feet : feet.getRelative(0, -1, 0);
        final World world = below.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius && magma.size() < MAX_MAGMA; z++) {
                if (!world.isChunkLoaded((below.getX() + x) >> 4, (below.getZ() + z) >> 4)) continue;
                final Block block = below.getRelative(x, 0, z);
                if ((block.getType() == Material.MAGMA_BLOCK
                        || block.getType() == Material.LAVA && ((Levelled) block.getBlockData()).getLevel() == 0)
                        && block.getRelative(0, 1, 0).getType().isAir()) {
                    final BlockState original = block.getState();
                    final BlockState replacement = block.getState();
                    replacement.setType(Material.BASALT);
                    final EntityBlockFormEvent event = new EntityBlockFormEvent(player, block, replacement);
                    Bukkit.getPluginManager().callEvent(event);
                    if (event.isCancelled() || block.getType() != original.getType()
                            || event.getNewState().getType() != Material.BASALT) continue;
                    listen();
                    final Chunk chunk = block.getChunk();
                    if (!tickets.containsKey(chunk)) chunk.addPluginChunkTicket(engine.plugin());
                    tickets.merge(chunk, 1, Integer::sum);
                    block.setType(Material.BASALT, false);
                    magma.put(block, original);
                    later(80, () -> restore(block));
                }
            }
        }
    }

    private void restore(Block block) {
        final BlockState original = magma.remove(block);
        if (original == null) return;
        final Chunk chunk = block.getChunk();
        if (block.getType() == Material.BASALT) original.update(true, false);
        if (tickets.merge(chunk, -1, Integer::sum) == 0) {
            tickets.remove(chunk);
            chunk.removePluginChunkTicket(engine.plugin());
        }
    }

    private BukkitTask later(int ticks, Runnable action) {
        final long epoch = generation;
        return Bukkit.getScheduler().runTaskLater(engine.plugin(), () -> {
            if (epoch == generation) action.run();
        }, ticks);
    }

    private void listen() {
        if (listening) return;
        Bukkit.getPluginManager().registerEvents(this, engine.plugin());
        listening = true;
    }

    void stop() {
        generation++;
        if (collectionCleanup != null) {
            collectionCleanup.cancel();
            collectionCleanup = null;
        }
        collections.clear();
        cooked = null;
        if (listening) {
            HandlerList.unregisterAll(this);
            listening = false;
        }
        for (Summon summon : summons.values()) {
            summon.timer.cancel();
            summon.mob.remove();
        }
        summons.clear();
        for (Block block : new java.util.ArrayList<>(magma.keySet())) restore(block);
    }

    private static final class Summon {
        final UUID owner, enemy;
        final Mob mob;
        final BukkitTask timer;

        Summon(UUID owner, UUID enemy, Mob mob, BukkitTask timer) {
            this.owner = owner;
            this.enemy = enemy;
            this.mob = mob;
            this.timer = timer;
        }
    }
}
