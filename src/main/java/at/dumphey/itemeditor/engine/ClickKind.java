package at.dumphey.itemeditor.engine;

import org.bukkit.event.block.Action;

/** Which click an INTERACT trigger is. (Shift is the player's sneaking state: use the {@code sneaking} condition.) */
public enum ClickKind {
    LEFT_AIR, LEFT_BLOCK, RIGHT_AIR, RIGHT_BLOCK;

    public boolean isLeft() {
        return this == LEFT_AIR || this == LEFT_BLOCK;
    }

    public boolean isRight() {
        return !isLeft();
    }

    public boolean isAir() {
        return this == LEFT_AIR || this == RIGHT_AIR;
    }

    public boolean isBlock() {
        return !isAir();
    }

    /** Null for physical interactions (pressure plates...), which are not clicks. */
    public static ClickKind of(Action action) {
        if (action == null) {
            return null;
        }
        switch (action) {
            case LEFT_CLICK_AIR:
                return LEFT_AIR;
            case LEFT_CLICK_BLOCK:
                return LEFT_BLOCK;
            case RIGHT_CLICK_AIR:
                return RIGHT_AIR;
            case RIGHT_CLICK_BLOCK:
                return RIGHT_BLOCK;
            default:
                return null;
        }
    }

    /** Bit mask of the clicks a name stands for: left, right, air, block, left-air, left-block, right-air, ... */
    public static int maskOf(String name) {
        switch (name.toLowerCase().replace('_', '-')) {
            case "any":
            case "all":
                return 15;
            case "left":
                return bit(LEFT_AIR) | bit(LEFT_BLOCK);
            case "right":
                return bit(RIGHT_AIR) | bit(RIGHT_BLOCK);
            case "air":
                return bit(LEFT_AIR) | bit(RIGHT_AIR);
            case "block":
                return bit(LEFT_BLOCK) | bit(RIGHT_BLOCK);
            case "left-air":
                return bit(LEFT_AIR);
            case "left-block":
                return bit(LEFT_BLOCK);
            case "right-air":
                return bit(RIGHT_AIR);
            case "right-block":
                return bit(RIGHT_BLOCK);
            default:
                return 0;
        }
    }

    public int bit() {
        return bit(this);
    }

    private static int bit(ClickKind kind) {
        return 1 << kind.ordinal();
    }
}
