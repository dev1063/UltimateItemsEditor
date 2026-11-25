package at.dumphey.itemeditor.engine;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.logging.Level;

/** Main-thread queue for optional effects whose cost can add up across players. */
final class CatalogWorkQueue {
    static final int AREA = 16;
    static final int WORLD_EDIT = 16;
    static final int MAGMA = 32;
    static final int SPAWN = 12;
    static final int HIT = 3;

    private final JavaPlugin plugin;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private BukkitTask worker;
    private int tick;
    private int budget = 64;
    private int capacity = 256;
    private long discarded;
    private boolean loggedFailure;

    CatalogWorkQueue(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    void loadLimits() {
        budget = Math.max(MAGMA, Math.min(256,
                plugin.getConfig().getInt("enchantment-performance.work-per-tick", 64)));
        capacity = Math.max(16, Math.min(1024,
                plugin.getConfig().getInt("enchantment-performance.max-queued-effects", 256)));
        discarded = 0;
        loggedFailure = false;
    }

    boolean offer(int cost, Runnable action) {
        if (jobs.size() >= capacity) {
            discarded++;
            return false;
        }
        jobs.addLast(new Job(cost, tick + 1, action));
        if (worker == null) {
            worker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drain, 1, 1);
        }
        return true;
    }

    private void drain() {
        final BukkitTask active = worker;
        tick++;
        int remaining = budget;
        while (worker == active && !jobs.isEmpty()) {
            final Job job = jobs.peekFirst();
            if (tick - job.ready > 10) {
                jobs.removeFirst();
                discarded++;
            } else if (tick - job.ready < 0 || job.cost > remaining) {
                break;
            } else {
                jobs.removeFirst();
                remaining -= job.cost;
                try {
                    job.action.run();
                } catch (RuntimeException ex) {
                    if (!loggedFailure) {
                        loggedFailure = true;
                        plugin.getLogger().log(Level.WARNING, "Queued enchantment effect failed", ex);
                    }
                }
            }
        }
        if (worker == active && jobs.isEmpty()) {
            active.cancel();
            worker = null;
            tick = 0;
        }
    }

    long discarded() {
        return discarded;
    }

    void stop() {
        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        jobs.clear();
        tick = 0;
    }

    private static final class Job {
        final int cost;
        final int ready;
        final Runnable action;

        Job(int cost, int ready, Runnable action) {
            this.cost = cost;
            this.ready = ready;
            this.action = action;
        }
    }
}
