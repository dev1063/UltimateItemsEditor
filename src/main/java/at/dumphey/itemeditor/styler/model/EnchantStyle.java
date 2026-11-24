package at.dumphey.itemeditor.styler.model;

import at.dumphey.itemeditor.styler.ColorFormat;
import at.dumphey.itemeditor.styler.Text;

/** How one enchantment is written in the lore. Placeholders: %name% %level_roman% %level_int% %description%. */
public final class EnchantStyle {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private final String id;
    private final boolean enabled;
    private final String display;
    private final String description;
    private final String formatString;
    private final int priority;

    public EnchantStyle(String id, boolean enabled, String display, String description, String formatString,
                        int priority) {
        this.id = id;
        this.enabled = enabled;
        this.display = display == null ? id : display;
        this.description = description == null ? "" : description;
        this.formatString = formatString == null ? "&7%name% %level_roman%" : formatString;
        this.priority = priority;
    }

    /** Legacy-formatted lore line. The overrides (from the language file) win over this style's own texts. */
    public String format(int level, String nameOverride, String descOverride) {
        final String name = !Text.isBlank(nameOverride) ? nameOverride : display;
        final String desc = !Text.isBlank(descOverride) ? descOverride : description;
        final String line = formatString
                .replace("%name%", name)
                .replace("%level_roman%", toRoman(level))
                .replace("%level_int%", String.valueOf(level))
                .replace("%description%", desc);
        return ColorFormat.toLegacy(line);
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }

    public int getPriority() {
        return priority;
    }

    public static String toRoman(int n) {
        if (n <= 0) {
            return String.valueOf(n);
        }
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (n >= VALUES[i]) {
                sb.append(SYMBOLS[i]);
                n -= VALUES[i];
            }
        }
        return sb.toString();
    }
}
