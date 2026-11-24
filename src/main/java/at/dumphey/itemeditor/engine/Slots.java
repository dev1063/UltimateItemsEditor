package at.dumphey.itemeditor.engine;

import org.bukkit.inventory.EquipmentSlot;

import java.util.List;

/** Equipment slots as bit masks, so "is this slot allowed" is a single AND. */
public final class Slots {

    public static final int M_HAND = 1;
    public static final int M_OFF_HAND = 2;
    public static final int M_FEET = 4;
    public static final int M_LEGS = 8;
    public static final int M_CHEST = 16;
    public static final int M_HEAD = 32;
    public static final int HANDS = M_HAND | M_OFF_HAND;
    public static final int ARMOR = M_FEET | M_LEGS | M_CHEST | M_HEAD;
    public static final int ANY = HANDS | ARMOR;

    /** SLOTS[i] has the bit (1 << i). */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.FEET,
            EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private Slots() {
    }

    public static int bit(EquipmentSlot slot) {
        if (slot == null) {
            return 0;
        }
        switch (slot) {
            case HAND:
                return M_HAND;
            case OFF_HAND:
                return M_OFF_HAND;
            case FEET:
                return M_FEET;
            case LEGS:
                return M_LEGS;
            case CHEST:
                return M_CHEST;
            case HEAD:
                return M_HEAD;
            default:
                return 0;
        }
    }

    /** A name or a list of names: hand, off_hand, head, chest, legs, feet, hands, armor, any. */
    public static int parse(Object value, ParseContext pc) throws ConfigException {
        final List<Object> names = Cfg.list(value);
        int mask = 0;
        for (Object o : names) {
            final String name = String.valueOf(o).toLowerCase().replace('-', '_');
            switch (name) {
                case "hand":
                case "main_hand":
                case "mainhand":
                    mask |= M_HAND;
                    break;
                case "off_hand":
                case "offhand":
                    mask |= M_OFF_HAND;
                    break;
                case "head":
                case "helmet":
                    mask |= M_HEAD;
                    break;
                case "chest":
                case "chestplate":
                    mask |= M_CHEST;
                    break;
                case "legs":
                case "leggings":
                    mask |= M_LEGS;
                    break;
                case "feet":
                case "boots":
                    mask |= M_FEET;
                    break;
                case "hands":
                    mask |= HANDS;
                    break;
                case "armor":
                    mask |= ARMOR;
                    break;
                case "any":
                case "all":
                    mask |= ANY;
                    break;
                default:
                    throw pc.error("unknown slot '" + o + "' (hand, off_hand, head, chest, legs, feet, hands, armor, any)");
            }
        }
        if (mask == 0) {
            throw pc.error("no slot given");
        }
        return mask;
    }
}
