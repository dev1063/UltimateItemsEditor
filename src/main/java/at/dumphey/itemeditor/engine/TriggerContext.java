package at.dumphey.itemeditor.engine;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Everything conditions and actions can look at. One instance is reused for every event (nothing is allocated per
 * event), so never keep a reference to it: actions that run later must use {@link #snapshot()}.
 */
public final class TriggerContext {

    TriggerEngine engine;
    public TriggerType type;
    public Event event;
    /** The player the rule is about (attacker, victim, clicker, killer...). Never null while a rule runs. */
    public Player player;
    /** The other side: victim / attacker / killed entity. */
    public Entity target;
    public Block block;
    public ClickKind click;
    /** The item involved and the slot it is in. */
    public ItemStack item;
    public EquipmentSlot slot;
    /** Custom item id of {@link #item}, and the level of the custom enchantment the running rule is bound to. */
    public String itemId;
    public int level;
    private ItemTagData tag;
    private boolean tagRead;
    public Rule rule;
    int commandsDispatched;
    public long tick;
    /** Milliseconds left of the cooldown that blocked the rule (only set while its cooldown message is built). */
    public long cooldownLeftMs;
    private Location location;
    private double damageValue = Double.NaN;

    void reset() {
        engine = null;
        type = null;
        event = null;
        player = null;
        target = null;
        block = null;
        click = null;
        item = null;
        slot = null;
        itemId = null;
        level = 0;
        tag = null;
        tagRead = false;
        rule = null;
        commandsDispatched = 0;
        tick = 0;
        cooldownLeftMs = 0;
        location = null;
        damageValue = Double.NaN;
    }

    public TriggerEngine engine() {
        return engine;
    }

    /** Custom data of {@link #item}: read from the item once, and only if a condition or rule asks for it. */
    public ItemTagData tags() {
        if (!tagRead) {
            tagRead = true;
            tag = engine == null || item == null ? null : engine.tags().read(item);
        }
        return tag;
    }

    /** Called by the engine when it already knows the data of {@link #item}. */
    void setTag(ItemTagData tag) {
        this.tag = tag;
        this.tagRead = true;
    }

    /** Called by the engine when {@link #item} changes. */
    void forgetTag() {
        this.tag = null;
        this.tagRead = false;
    }

    public Location location() {
        if (location == null && player != null) {
            location = player.getLocation();
        }
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public boolean isCancelled() {
        if (event instanceof PlayerInteractEvent) {
            return isInteractionCancelled((PlayerInteractEvent) event);
        }
        return event instanceof Cancellable && ((Cancellable) event).isCancelled();
    }

    static boolean isInteractionCancelled(PlayerInteractEvent event) {
        // Bukkit pre-denies block use for air clicks. A denied item use still blocks item actions.
        return event.useItemInHand() == Event.Result.DENY
                || (event.getClickedBlock() != null && event.useInteractedBlock() == Event.Result.DENY);
    }

    public void cancel() {
        if (event instanceof Cancellable) {
            ((Cancellable) event).setCancelled(true);
        }
    }

    /** Damage of the damage event (ATTACK / DEFEND), or 0. */
    public double getDamage() {
        if (event instanceof EntityDamageEvent) {
            return ((EntityDamageEvent) event).getDamage();
        }
        return Double.isNaN(damageValue) ? 0 : damageValue;
    }

    /** Changes the damage of the running damage event. No effect for other triggers. */
    public void setDamage(double damage) {
        if (event instanceof EntityDamageEvent) {
            ((EntityDamageEvent) event).setDamage(Math.max(0, damage));
        }
    }

    /** A copy that stays valid after the event is over (the event itself is dropped). */
    public TriggerContext snapshot() {
        final TriggerContext copy = new TriggerContext();
        copy.engine = engine;
        copy.type = type;
        copy.player = player;
        copy.target = target;
        copy.block = block;
        copy.click = click;
        copy.item = item == null ? null : item.clone();
        copy.slot = slot;
        copy.itemId = itemId;
        copy.level = level;
        copy.tag = tag;
        copy.tagRead = tagRead;
        copy.rule = rule;
        copy.tick = tick;
        copy.location = location == null ? null : location.clone();
        copy.damageValue = getDamage();
        return copy;
    }
}
