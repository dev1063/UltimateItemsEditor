package at.dumphey.itemeditor.engine;

import java.util.Locale;

/**
 * What a player does (or has done to them) that rules can react to.
 *
 * <p>Subject-based triggers are about one particular item: the item involved in the action (the clicked item, the
 * tool that broke the block, the consumed food...). Rules bound to a custom item/enchantment look at that item.
 * The other triggers scan the equipment slots the rule lists in {@code slots} (weapon, armor...).
 */
public enum TriggerType {
    /** Left/right click in the air or on a block. */
    INTERACT(true),
    BLOCK_BREAK(true),
    BLOCK_DAMAGE(true),
    FISH_HOOK(true),
    ITEM_WEAR(true),
    INJURY(false),
    FLIGHT_USE(false),
    THROW_TRIDENT(true),
    BLOCK_PLACE(true),
    CONSUME(true),
    DROP(true),
    SHOOT_BOW(true),
    /** The player damages an entity (melee or with a projectile). */
    ATTACK(false),
    /** The player is damaged by an entity. */
    DEFEND(false),
    /** The player kills an entity. */
    KILL(false),
    /** The player dies. */
    DEATH(false),
    JOIN(false),
    QUIT(false),
    /** Every few seconds for every online player (passive effects). */
    PERIODIC(false);

    private final boolean subjectBased;

    TriggerType(boolean subjectBased) {
        this.subjectBased = subjectBased;
    }

    public boolean isSubjectBased() {
        return subjectBased;
    }

    /** "block-break", "BLOCK_BREAK" and "block break" all work. Null if unknown. */
    public static TriggerType parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
