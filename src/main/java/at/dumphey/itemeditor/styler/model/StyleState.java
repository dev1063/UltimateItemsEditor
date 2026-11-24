package at.dumphey.itemeditor.styler.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the styler remembers about an item it styled (stored in the item's persistent data), so that a template
 * can be re-applied without stacking and removed again without losing the player's own edits.
 */
public final class StyleState {

    private final String template;
    private final String originalName;
    private final List<String> originalLore;
    private final int originalFlags;
    private final String generatedName;
    private final List<String> generatedHead;
    private final List<String> generatedTail;

    public StyleState(String template, String originalName, List<String> originalLore, int originalFlags,
                      String generatedName, List<String> generatedHead, List<String> generatedTail) {
        this.template = template;
        this.originalName = originalName;
        this.originalLore = Collections.unmodifiableList(new ArrayList<>(originalLore));
        this.originalFlags = originalFlags;
        this.generatedName = generatedName;
        this.generatedHead = Collections.unmodifiableList(new ArrayList<>(generatedHead));
        this.generatedTail = Collections.unmodifiableList(new ArrayList<>(generatedTail));
    }

    /** Name of the applied template. */
    public String getTemplate() {
        return template;
    }

    /** Display name before the template was applied (null = the item had no custom name). */
    public String getOriginalName() {
        return originalName;
    }

    /** Lore before the template was applied. */
    public List<String> getOriginalLore() {
        return originalLore;
    }

    /** Bit 1 = HIDE_ENCHANTS, bit 2 = HIDE_ATTRIBUTES, as they were before the template. */
    public int getOriginalFlags() {
        return originalFlags;
    }

    /** Display name exactly as the template wrote it (null = none). */
    public String getGeneratedName() {
        return generatedName;
    }

    /** Lore lines the template placed before the item's own lore. */
    public List<String> getGeneratedHead() {
        return generatedHead;
    }

    /** Lore lines the template placed after the item's own lore. */
    public List<String> getGeneratedTail() {
        return generatedTail;
    }
}
