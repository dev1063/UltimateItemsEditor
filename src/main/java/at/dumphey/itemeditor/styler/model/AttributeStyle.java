package at.dumphey.itemeditor.styler.model;

import at.dumphey.itemeditor.styler.ColorFormat;
import at.dumphey.itemeditor.styler.Text;
import org.bukkit.attribute.AttributeModifier;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** How one attribute modifier is written in the lore. Placeholders: %value% %display% %icon%. */
public final class AttributeStyle {

    private final String id;
    private final boolean enabled;
    private final String display;
    private final String icon;
    private final String formatString;
    private final int priority;

    public AttributeStyle(String id, boolean enabled, String display, String icon, String formatString,
                          int priority) {
        this.id = id;
        this.enabled = enabled;
        this.display = display == null ? id : display;
        this.icon = icon == null ? "" : icon;
        this.formatString = formatString == null ? "&7%icon% %value% &7%display%" : formatString;
        this.priority = priority;
    }

    public String format(double value, AttributeModifier.Operation operation, int decimals, String displayOverride) {
        final String disp = !Text.isBlank(displayOverride) ? displayOverride : display;
        final String line = formatString
                .replace("%value%", formatValue(value, operation, decimals))
                .replace("%display%", disp)
                .replace("%icon%", icon);
        return ColorFormat.toLegacy(line);
    }

    /** +5.00 for ADD_NUMBER, +10.00% for ADD_SCALAR, x1.10 for MULTIPLY_SCALAR_1. */
    public static String formatValue(double value, AttributeModifier.Operation op, int decimals) {
        final String pattern = decimals > 0 ? "0." + Text.repeat('0', decimals) : "0";
        final DecimalFormat df = new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(Locale.ROOT));
        if (op == AttributeModifier.Operation.ADD_SCALAR) {
            return (value >= 0 ? "+" : "") + df.format(value * 100.0) + "%";
        }
        if (op == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
            return "\u00d7" + df.format(1.0 + value);
        }
        return (value >= 0 ? "+" : "") + df.format(value);
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

    public int getPriority() {
        return priority;
    }
}
