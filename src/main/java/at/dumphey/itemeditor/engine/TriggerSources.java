package at.dumphey.itemeditor.engine;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;

/** The built-in trigger sources. */
final class TriggerSources {

    private TriggerSources() {
    }

    private abstract static class Source<E extends Event> implements TriggerSource {
        private final TriggerType type;
        private final Class<E> eventClass;
        private final EventPriority priority;

        Source(TriggerType type, Class<E> eventClass, EventPriority priority) {
            this.type = type;
            this.eventClass = eventClass;
            this.priority = priority;
        }

        @Override
        public TriggerType type() {
            return type;
        }

        @Override
        public Class<? extends Event> eventClass() {
            return eventClass;
        }

        @Override
        public EventPriority priority() {
            return priority;
        }

        @Override
        public boolean fill(Event event, TriggerContext ctx) {
            return fillTyped(eventClass.cast(event), ctx);
        }

        abstract boolean fillTyped(E event, TriggerContext ctx);
    }

    static List<TriggerSource> defaults() {
        final List<TriggerSource> list = new ArrayList<>();

        list.add(new Source<PlayerInteractEvent>(TriggerType.INTERACT, PlayerInteractEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(PlayerInteractEvent e, TriggerContext c) {
                final ClickKind kind = ClickKind.of(e.getAction());
                if (kind == null) {
                    return false; // physical interaction: not a click
                }
                c.player = e.getPlayer();
                c.click = kind;
                c.block = e.getClickedBlock();
                c.item = e.getItem();
                c.slot = e.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                return true;
            }
        });

        list.add(new Source<BlockBreakEvent>(TriggerType.BLOCK_BREAK, BlockBreakEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(BlockBreakEvent e, TriggerContext c) {
                c.player = e.getPlayer();
                c.block = e.getBlock();
                c.item = c.player.getInventory().getItemInMainHand();
                c.slot = EquipmentSlot.HAND;
                return true;
            }
        });

        list.add(new Source<BlockPlaceEvent>(TriggerType.BLOCK_PLACE, BlockPlaceEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(BlockPlaceEvent e, TriggerContext c) {
                c.player = e.getPlayer();
                c.block = e.getBlockPlaced();
                c.item = e.getItemInHand();
                c.slot = e.getHand() == EquipmentSlot.OFF_HAND ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                return true;
            }
        });

        list.add(new Source<PlayerItemConsumeEvent>(TriggerType.CONSUME, PlayerItemConsumeEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(PlayerItemConsumeEvent e, TriggerContext c) {
                c.player = e.getPlayer();
                c.item = e.getItem();
                c.slot = EquipmentSlot.HAND;
                return true;
            }
        });

        list.add(new Source<PlayerDropItemEvent>(TriggerType.DROP, PlayerDropItemEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(PlayerDropItemEvent e, TriggerContext c) {
                c.player = e.getPlayer();
                c.item = e.getItemDrop().getItemStack();
                c.slot = EquipmentSlot.HAND;
                return true;
            }
        });

        list.add(new Source<EntityShootBowEvent>(TriggerType.SHOOT_BOW, EntityShootBowEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(EntityShootBowEvent e, TriggerContext c) {
                if (!(e.getEntity() instanceof Player)) {
                    return false;
                }
                c.player = (Player) e.getEntity();
                c.item = e.getBow();
                c.slot = EquipmentSlot.HAND;
                return true;
            }
        });

        list.add(new Source<EntityDamageByEntityEvent>(TriggerType.ATTACK, EntityDamageByEntityEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(EntityDamageByEntityEvent e, TriggerContext c) {
                final Player attacker = attackerOf(e.getDamager());
                if (attacker == null) {
                    return false;
                }
                c.player = attacker;
                c.target = e.getEntity();
                return true;
            }
        });

        list.add(new Source<EntityDamageByEntityEvent>(TriggerType.DEFEND, EntityDamageByEntityEvent.class, EventPriority.HIGH) {
            @Override
            boolean fillTyped(EntityDamageByEntityEvent e, TriggerContext c) {
                if (!(e.getEntity() instanceof Player)) {
                    return false;
                }
                c.player = (Player) e.getEntity();
                c.target = sourceOf(e.getDamager());
                return true;
            }
        });

        // Note: Bukkit only calls EntityDeathEvent listeners for the entity events whose handler list is shared;
        // DEATH uses PlayerDeathEvent explicitly.
        list.add(new Source<EntityDeathEvent>(TriggerType.KILL, EntityDeathEvent.class, EventPriority.NORMAL) {
            @Override
            boolean fillTyped(EntityDeathEvent e, TriggerContext c) {
                final Player killer = e.getEntity().getKiller();
                if (killer == null) {
                    return false;
                }
                c.player = killer;
                c.target = e.getEntity();
                return true;
            }
        });

        list.add(new Source<PlayerDeathEvent>(TriggerType.DEATH, PlayerDeathEvent.class, EventPriority.NORMAL) {
            @Override
            boolean fillTyped(PlayerDeathEvent e, TriggerContext c) {
                c.player = e.getEntity();
                c.target = e.getEntity().getKiller();
                return true;
            }
        });

        list.add(new Source<PlayerJoinEvent>(TriggerType.JOIN, PlayerJoinEvent.class, EventPriority.NORMAL) {
            @Override
            boolean fillTyped(PlayerJoinEvent e, TriggerContext c) {
                c.player = e.getPlayer();
                return true;
            }
        });

        list.add(new Source<PlayerQuitEvent>(TriggerType.QUIT, PlayerQuitEvent.class, EventPriority.NORMAL) {
            @Override
            boolean fillTyped(PlayerQuitEvent e, TriggerContext c) {
                c.player = e.getPlayer();
                return true;
            }
        });

        return list;
    }

    /** The player behind a damage event: the damager itself or the shooter of the projectile. */
    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player) {
            return (Player) damager;
        }
        if (damager instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Player) {
                return (Player) shooter;
            }
        }
        return null;
    }

    /** Who really hit: the shooter for projectiles, the damager otherwise. */
    private static Entity sourceOf(Entity damager) {
        if (damager instanceof Projectile) {
            final ProjectileSource shooter = ((Projectile) damager).getShooter();
            if (shooter instanceof Entity) {
                return (Entity) shooter;
            }
        }
        return damager;
    }
}
