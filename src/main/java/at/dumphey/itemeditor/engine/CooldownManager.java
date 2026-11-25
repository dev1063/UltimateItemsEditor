package at.dumphey.itemeditor.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Per-player cooldowns by key. Memory is released when the player leaves and expired keys are pruned lazily. */
public final class CooldownManager {

    private static final int PRUNE_ABOVE = 16;

    private final Map<UUID, Map<String, Long>> expiries = new HashMap<>();
    private final LongSupplier clock;

    public CooldownManager() {
        this(System::currentTimeMillis);
    }

    public CooldownManager(LongSupplier clock) {
        this.clock = clock;
    }

    /** If the key is ready, starts its cooldown and returns true. */
    public boolean tryUse(UUID player, String key, long durationMs) {
        final long now = clock.getAsLong();
        final Map<String, Long> map = expiries.computeIfAbsent(player, keyId -> new HashMap<>());
        final Long until = map.get(key);
        if (until != null && until > now) {
            return false;
        }
        put(map, key, now, durationMs);
        return true;
    }

    public boolean isReady(UUID player, String key) {
        return remaining(player, key) == 0;
    }

    public long remaining(UUID player, String key) {
        final Map<String, Long> map = expiries.get(player);
        if (map == null) {
            return 0;
        }
        final Long until = map.get(key);
        return until == null ? 0 : Math.max(0, until - clock.getAsLong());
    }

    public void set(UUID player, String key, long durationMs) {
        Map<String, Long> map = expiries.get(player);
        if (map == null) {
            map = new HashMap<>();
            expiries.put(player, map);
        }
        final long now = clock.getAsLong();
        put(map, key, now, durationMs);
    }

    private void put(Map<String, Long> map, String key, long now, long durationMs) {
        final Long previous = map.put(key, now + durationMs);
        if (previous == null && map.size() > PRUNE_ABOVE) {
            for (Iterator<Long> it = map.values().iterator(); it.hasNext(); ) {
                if (it.next() <= now) {
                    it.remove();
                }
            }
        }
    }

    public void clear(UUID player) {
        expiries.remove(player);
    }

    public void clearAll() {
        expiries.clear();
    }

    public int trackedPlayers() {
        return expiries.size();
    }
}
