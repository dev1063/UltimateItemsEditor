package at.dumphey.itemeditor.styler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A configurable item template ("palette"): the frame placed around an item's lore, a display-name prefix and
 * the tooltip flags to use.
 */
public final class Template {

    private final String name;
    private final String icon;
    private final String displayNamePrefix;
    private final List<String> header;
    private final List<String> footer;
    private final boolean enchantSpacing;
    private final boolean attributeSpacing;
    private final boolean hideEnchants;
    private final boolean hideAttributes;

    public Template(String name, String icon, String displayNamePrefix, List<String> header, List<String> footer,
                    boolean enchantSpacing, boolean attributeSpacing, boolean hideEnchants,
                    boolean hideAttributes) {
        this.name = name;
        this.icon = icon;
        this.displayNamePrefix = displayNamePrefix == null ? "" : displayNamePrefix;
        this.header = Collections.unmodifiableList(new ArrayList<>(header == null ? new ArrayList<String>() : header));
        this.footer = Collections.unmodifiableList(new ArrayList<>(footer == null ? new ArrayList<String>() : footer));
        this.enchantSpacing = enchantSpacing;
        this.attributeSpacing = attributeSpacing;
        this.hideEnchants = hideEnchants;
        this.hideAttributes = hideAttributes;
    }

    public String getName() {
        return name;
    }

    /** Material name used as icon in the GUI, or null. */
    public String getIcon() {
        return icon;
    }

    public String getDisplayNamePrefix() {
        return displayNamePrefix;
    }

    public List<String> getHeader() {
        return header;
    }

    public List<String> getFooter() {
        return footer;
    }

    public boolean isEnchantSpacing() {
        return enchantSpacing;
    }

    public boolean isAttributeSpacing() {
        return attributeSpacing;
    }

    public boolean isHideEnchants() {
        return hideEnchants;
    }

    public boolean isHideAttributes() {
        return hideAttributes;
    }

    @Override
    public String toString() {
        return "Template{name='" + name + "'}";
    }
}
