package at.dumphey.itemeditor.utils;

/** Lore/name formatting of the GUI items (plain concatenation: String.format is slow and this runs on every render). */
public class Format {

    public static String description(String description) {
        return "§8§l* §8" + description;
    }

    public static String name(String name) {
        return "§e" + name;
    }

    public static String descriptionContinued(String descriptionContinued) {
        return "  §8" + descriptionContinued;
    }
}
