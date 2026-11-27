package at.dumphey.itemeditor.engine;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

import java.util.logging.Level;

/** Optional MythicMobs 5 bridge. API signatures are resolved on reload, only when MythicMobs is enabled. */
final class MythicMobLookup {
    private final JavaPlugin owner;
    private Plugin dependency;
    private Object helper;
    private Method lookup;
    private Method type;
    private Method internalName;

    MythicMobLookup(JavaPlugin owner) {
        this.owner = owner;
    }

    void load() {
        stop();
        if (!owner.getConfig().getBoolean("integrations.mythicmobs.enabled", true)) return;
        final Plugin plugin = owner.getServer().getPluginManager().getPlugin("MythicMobs");
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            final ClassLoader loader = plugin.getClass().getClassLoader();
            final Class<?> api = Class.forName("io.lumine.mythic.bukkit.BukkitAPIHelper", true, loader);
            final Method find = api.getMethod("getMythicMobInstance", Entity.class);
            final Method mobType = find.getReturnType().getMethod("getType");
            final Method name = mobType.getReturnType().getMethod("getInternalName");
            helper = api.getConstructor().newInstance();
            lookup = find;
            type = mobType;
            internalName = name;
            dependency = plugin;
            owner.getLogger().info("MythicMobs target conditions enabled.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            stop();
            owner.getLogger().log(Level.WARNING, "MythicMobs API is unavailable; its target conditions will not match.", ex);
        }
    }

    boolean isAvailable() {
        return dependency != null && dependency.isEnabled();
    }

    String internalName(Entity target) {
        if (!(target instanceof LivingEntity) || target instanceof Player || !isAvailable()) return null;
        try {
            final Object active = lookup.invoke(helper, target);
            if (active == null) return null;
            final Object definition = type.invoke(active);
            return definition == null ? null : (String) internalName.invoke(definition);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            stop();
            owner.getLogger().log(Level.WARNING, "MythicMobs lookup stopped after an API error. Reload to retry.", ex);
            return null;
        }
    }

    void stop() {
        dependency = null;
        helper = null;
        lookup = null;
        type = null;
        internalName = null;
    }
}
