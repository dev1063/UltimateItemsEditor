package at.dumphey.itemeditor.engine;

import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;

/**
 * Connects one {@link TriggerType} to the Bukkit event it comes from. The engine registers the event listener only
 * while at least one rule uses the trigger.
 */
public interface TriggerSource {

    TriggerType type();

    Class<? extends Event> eventClass();

    EventPriority priority();

    /**
     * Copies what rules need from the event into the (empty) context.
     *
     * @return false if this event is not relevant (for example the damager is not a player)
     */
    boolean fill(Event event, TriggerContext ctx);
}
