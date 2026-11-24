package at.dumphey.itemeditor.engine;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

final class CatalogMining {
    private static final int MAX_JOBS = 64;
    private static final int BLOCKS_PER_TICK = 24;
    private static final int MAX_TUNNEL_LEVEL = 4;
    private static final int[][] FACES = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};

    private final TriggerEngine engine;
    private final Queue<Job> jobs = new ArrayDeque<>();
    private final Set<UUID> pending = new HashSet<>();
    private BukkitTask worker;
    private boolean breaking;

    CatalogMining(TriggerEngine engine) {
        this.engine = engine;
        engine.registerAction("catalog-mine", (cfg, pc) -> {
            final Mode mode;
            try {
                mode = Mode.valueOf(String.valueOf(cfg).toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw pc.error("expected tunnel, vein or tree");
            }
            return ctx -> queue(ctx, mode);
        });
    }

    private void queue(TriggerContext ctx, Mode mode) {
        if (breaking || jobs.size() >= MAX_JOBS || !(ctx.event instanceof BlockBreakEvent)
                || ctx.player.getGameMode() != GameMode.SURVIVAL || ctx.player.isSneaking() || ctx.item == null) {
            return;
        }
        final Material root = ctx.block.getType();
        if (!mode.accepts(root) || !pending.add(ctx.player.getUniqueId())) {
            return;
        }
        jobs.add(new Job(ctx, mode));
        if (worker == null) {
            worker = engine.plugin().getServer().getScheduler().runTaskTimer(engine.plugin(), this::tick, 1, 1);
        }
    }

    private void tick() {
        final BukkitTask running = worker;
        int remaining = BLOCKS_PER_TICK;
        while (remaining-- > 0 && !jobs.isEmpty()) {
            final Job job = jobs.remove();
            try {
                final boolean more = job.step();
                if (worker != running) {
                    return;
                }
                if (more) {
                    jobs.add(job);
                    continue;
                }
            } catch (RuntimeException ex) {
                engine.plugin().getLogger().log(java.util.logging.Level.WARNING, "Queued excavation failed", ex);
            }
            pending.remove(job.player.getUniqueId());
        }
        if (jobs.isEmpty() && worker == running) {
            running.cancel();
            worker = null;
        }
    }

    void stop() {
        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        jobs.clear();
        pending.clear();
    }

    private enum Mode {
        TUNNEL, VEIN, TREE;

        boolean accepts(Material material) {
            if (this == TUNNEL) {
                return TunnelMaterials.ALLOWED.contains(material);
            }
            final String name = material.name();
            return this == VEIN ? name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS
                    : name.endsWith("_LOG") || name.endsWith("_STEM") && !name.equals("MUSHROOM_STEM");
        }
    }

    private static final class TunnelMaterials {
        // A geological allowlist keeps containers, machines and decorations out of area digging.
        static final Set<Material> ALLOWED = java.util.EnumSet.of(Material.STONE, Material.COBBLESTONE,
                Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.CALCITE, Material.GRANITE,
                Material.DIORITE, Material.ANDESITE, Material.NETHERRACK, Material.BLACKSTONE, Material.BASALT,
                Material.SMOOTH_BASALT, Material.END_STONE, Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT,
                Material.ROOTED_DIRT, Material.PODZOL, Material.SAND, Material.RED_SAND, Material.GRAVEL,
                Material.CLAY, Material.SOUL_SAND, Material.SOUL_SOIL, Material.MUD, Material.MYCELIUM);
    }

    private final class Job {
        final Player player;
        final World world;
        BlockBreakEvent original;
        final Material material;
        final Material tool;
        final Material alternateOre;
        final int slot;
        final int x, y, z;
        final Mode mode;
        final int axis;
        final int width;
        final int limit;
        final Queue<int[]> candidates = new ArrayDeque<>();
        final Set<Long> visited = new HashSet<>();
        int position;
        int mined;

        Job(TriggerContext ctx, Mode mode) {
            this.player = ctx.player;
            this.world = ctx.block.getWorld();
            this.original = (BlockBreakEvent) ctx.event;
            this.material = ctx.block.getType();
            this.tool = ctx.item.getType();
            final String name = material.name();
            this.alternateOre = mode == Mode.VEIN ? Material.getMaterial(name.startsWith("DEEPSLATE_")
                    ? name.substring("DEEPSLATE_".length()) : "DEEPSLATE_" + name) : null;
            this.slot = player.getInventory().getHeldItemSlot();
            this.x = ctx.block.getX();
            this.y = ctx.block.getY();
            this.z = ctx.block.getZ();
            this.mode = mode;
            final Location view = player.getLocation();
            final double yaw = Math.toRadians(view.getYaw());
            this.axis = Math.abs(view.getPitch()) > 55 ? 1
                    : (Math.abs(Math.sin(yaw)) > Math.abs(Math.cos(yaw)) ? 0 : 2);
            width = 2 + Math.min(MAX_TUNNEL_LEVEL, Math.max(1, ctx.level));
            limit = mode == Mode.TREE ? Math.min(96, 24 * Math.max(1, ctx.level))
                    : Math.min(48, 12 * Math.max(1, ctx.level));
            if (mode != Mode.TUNNEL) {
                visited.add(0L);
                enqueueNeighbors(0, 0, 0);
            }
        }

        boolean step() {
            if (!player.isOnline() || player.getGameMode() != GameMode.SURVIVAL || player.getWorld() != world
                    || player.getInventory().getHeldItemSlot() != slot
                    || player.getInventory().getItemInMainHand().getType() != tool) {
                return false;
            }
            if (original != null) {
                if (original.isCancelled() || !world.isChunkLoaded(x >> 4, z >> 4)
                        || world.getBlockAt(x, y, z).getType() == material) {
                    return false;
                }
                original = null;
            }
            final Location location = player.getLocation();
            if (Math.abs(location.getBlockX() - x) > 16 || Math.abs(location.getBlockY() - y) > 16
                    || Math.abs(location.getBlockZ() - z) > 16) {
                return false;
            }
            if (mode == Mode.TUNNEL) {
                if (position >= width * width) {
                    return false;
                }
                final int u = position % width - width / 2;
                final int v = position++ / width - width / 2;
                if (u != 0 || v != 0) {
                    mine(axis == 0 ? 0 : u, axis == 1 ? 0 : v, axis == 2 ? 0 : (axis == 0 ? u : v));
                }
                return position < width * width;
            }
            final int[] next = candidates.poll();
            if (next == null) {
                return false;
            }
            if (mine(next[0], next[1], next[2])) {
                mined++;
                enqueueNeighbors(next[0], next[1], next[2]);
            }
            return mined < limit && !candidates.isEmpty();
        }

        private boolean mine(int dx, int dy, int dz) {
            if (y + dy < world.getMinHeight() || y + dy >= world.getMaxHeight()
                    || !world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) {
                return false;
            }
            final Block candidate = world.getBlockAt(x + dx, y + dy, z + dz);
            final Material type = candidate.getType();
            if (mode == Mode.TUNNEL ? !mode.accepts(type) : type != material && type != alternateOre) {
                return false;
            }
            if (!candidate.isPreferredTool(player.getInventory().getItemInMainHand())) {
                return false;
            }
            breaking = true;
            try {
                return player.breakBlock(candidate);
            } finally {
                breaking = false;
            }
        }

        private void enqueueNeighbors(int dx, int dy, int dz) {
            if (mode == Mode.TREE) {
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            if (ox != 0 || oy != 0 || oz != 0) enqueue(dx + ox, dy + oy, dz + oz);
                        }
                    }
                }
            } else {
                for (int[] face : FACES) enqueue(dx + face[0], dy + face[1], dz + face[2]);
            }
        }

        private void enqueue(int dx, int dy, int dz) {
            if (Math.abs(dx) > 12 || Math.abs(dz) > 12 || dy < -12 || dy > 32) {
                return;
            }
            final long key = ((dx & 63L) << 12) | ((dy & 63L) << 6) | (dz & 63L);
            if (visited.add(key)) {
                candidates.add(new int[]{dx, dy, dz});
            }
        }
    }
}
