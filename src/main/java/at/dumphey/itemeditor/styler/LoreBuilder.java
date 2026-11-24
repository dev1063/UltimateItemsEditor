package at.dumphey.itemeditor.styler;

import at.dumphey.itemeditor.styler.model.AttributeStyle;
import at.dumphey.itemeditor.styler.model.EnchantStyle;
import at.dumphey.itemeditor.styler.model.StyleState;
import at.dumphey.itemeditor.styler.model.Template;
import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Applies a {@link Template} to an item and takes it off again.
 *
 * <pre>
 * lore = template header + the item's own lore + enchantment lines + attribute lines + template footer
 * </pre>
 *
 * The item remembers (see {@link StyleState}) what it looked like before and what was generated, so:
 * re-applying another template never stacks, a name or lore the player changed in between is kept, and
 * {@link #reset} restores the item.
 */
public class LoreBuilder {

    /** Explicit reset in front of every generated line: lore would otherwise render in italics. */
    private static final String LINE_PREFIX = "\u00a7r";

    private static final int FLAG_ENCHANTS = 1;
    private static final int FLAG_ATTRIBUTES = 2;

    private final StyleConfig config;
    private final LanguageManager lang;
    private final EnchantStyleManager enchants;
    private final AttributeStyleManager attributes;
    private final MetadataManager metadata;

    public LoreBuilder(StyleConfig config, LanguageManager lang, EnchantStyleManager enchants,
                       AttributeStyleManager attributes, MetadataManager metadata) {
        this.config = config;
        this.lang = lang;
        this.enchants = enchants;
        this.attributes = attributes;
        this.metadata = metadata;
    }

    public boolean apply(ItemStack item, Template template) {
        if (item == null || template == null || item.getType() == Material.AIR) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        final StyleState previous = metadata.read(meta);
        final List<String> currentLore = currentLore(meta);
        final String currentName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        final boolean keepLore = config.isKeepItemLore();

        // What the item looked like before any template (taking the player's later edits into account).
        final String baseName;
        final List<String> baseLore;
        final int originalFlags;
        if (previous == null) {
            baseName = currentName;
            baseLore = currentLore;
            originalFlags = flagsOf(meta);
        } else {
            originalFlags = previous.getOriginalFlags();
            baseName = Objects.equals(currentName, previous.getGeneratedName()) ? previous.getOriginalName() : currentName;
            final List<String> extracted = keepLore ? extractBase(currentLore, previous) : null;
            baseLore = extracted != null ? extracted : previous.getOriginalLore();
        }

        // Lore
        final List<String> head = renderLines(template.getHeader());
        final List<String> tail = new ArrayList<>();
        final List<EnchantEntry> enchantEntries = enchantEntries(item);
        final List<AttrEntry> attrEntries = attributeEntries(meta);

        for (EnchantEntry e : enchantEntries) {
            if (!e.style.isEnabled()) {
                continue;
            }
            final String key = e.enchant.getKey().getKey();
            tail.add(LINE_PREFIX + e.style.format(e.level, lang.getEnchantName(key), lang.getEnchantDescription(key)));
        }
        if (template.isEnchantSpacing() && !enchantEntries.isEmpty() && !attrEntries.isEmpty()) {
            tail.add("");
        }
        final int decimals = config.getAttributeDecimalPlaces();
        int attrLines = 0;
        for (AttrEntry a : attrEntries) {
            if (!a.style.isEnabled()) {
                continue;
            }
            final String key = a.attribute.getKey().getKey();
            tail.add(LINE_PREFIX + a.style.format(a.modifier.getAmount(), a.modifier.getOperation(), decimals,
                    lang.getAttributeName(AttributeStyleManager.normalize(key))));
            attrLines++;
        }
        if (template.isAttributeSpacing() && attrLines > 0) {
            tail.add("");
        }
        tail.addAll(renderLines(template.getFooter()));

        final List<String> lore = new ArrayList<>(head);
        if (keepLore) {
            lore.addAll(baseLore);
        }
        lore.addAll(tail);
        meta.setLore(lore.isEmpty() ? null : lore);

        // Display name
        final String prefixRaw = lang.resolveTemplateText(template.getDisplayNamePrefix());
        if (!Text.isBlank(prefixRaw)) {
            final String name = baseName != null ? baseName : friendlyName(item.getType());
            meta.setDisplayName(ColorFormat.toLegacyPrefix(prefixRaw) + name);
        } else {
            meta.setDisplayName(baseName);
        }

        // Tooltip flags
        setFlag(meta, ItemFlag.HIDE_ENCHANTS, template.isHideEnchants() || config.isHideEnchantsGlobally());
        setFlag(meta, ItemFlag.HIDE_ATTRIBUTES, template.isHideAttributes() || config.isHideAttributesGlobally());

        // Remember the canonical form Bukkit stored, so the comparison on the next apply/reset is exact.
        final List<String> storedLore = currentLore(meta);
        final int h = head.size();
        final int t = tail.size();
        final List<String> generatedHead = new ArrayList<>();
        final List<String> generatedTail = new ArrayList<>();
        if (storedLore.size() >= h + t) {
            generatedHead.addAll(storedLore.subList(0, h));
            generatedTail.addAll(storedLore.subList(storedLore.size() - t, storedLore.size()));
        }
        final String generatedName = meta.hasDisplayName() ? meta.getDisplayName() : null;

        metadata.write(meta, new StyleState(template.getName(), baseName, baseLore, originalFlags, generatedName,
                generatedHead, generatedTail));
        item.setItemMeta(meta);
        return true;
    }

    /** Takes the template off. Returns false if the item has no template. */
    public boolean reset(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        final ItemMeta meta = item.getItemMeta();
        final StyleState state = metadata.read(meta);
        if (state == null) {
            return false;
        }

        final String currentName = meta.hasDisplayName() ? meta.getDisplayName() : null;
        if (Objects.equals(currentName, state.getGeneratedName())) {
            meta.setDisplayName(state.getOriginalName());
        } // else: the player renamed the item after the template; keep their name

        List<String> base = extractBase(currentLore(meta), state);
        if (base == null || (base.isEmpty() && !state.getOriginalLore().isEmpty())) {
            base = state.getOriginalLore();
        }
        meta.setLore(base.isEmpty() ? null : new ArrayList<>(base));

        setFlag(meta, ItemFlag.HIDE_ENCHANTS, (state.getOriginalFlags() & FLAG_ENCHANTS) != 0);
        setFlag(meta, ItemFlag.HIDE_ATTRIBUTES, (state.getOriginalFlags() & FLAG_ATTRIBUTES) != 0);

        metadata.clear(meta);
        item.setItemMeta(meta);
        return true;
    }

    /** The template's header or footer as it will appear on an item (also used for GUI previews). */
    public List<String> renderLines(List<String> lines) {
        final List<String> out = new ArrayList<>();
        for (String line : lines) {
            final String resolved = lang.resolveTemplateText(line);
            out.add(Text.isBlank(resolved) ? "" : LINE_PREFIX + ColorFormat.toLegacy(resolved));
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> currentLore(ItemMeta meta) {
        final List<String> lore = meta.hasLore() ? meta.getLore() : null;
        return lore == null ? new ArrayList<String>() : new ArrayList<>(lore);
    }

    /** The item's own lore between what the template put before and after it, or null if it was tampered with. */
    private static List<String> extractBase(List<String> current, StyleState state) {
        final int h = state.getGeneratedHead().size();
        final int t = state.getGeneratedTail().size();
        if (current.size() < h + t) {
            return null;
        }
        if (!current.subList(0, h).equals(state.getGeneratedHead())) {
            return null;
        }
        if (!current.subList(current.size() - t, current.size()).equals(state.getGeneratedTail())) {
            return null;
        }
        return new ArrayList<>(current.subList(h, current.size() - t));
    }

    private static int flagsOf(ItemMeta meta) {
        int flags = 0;
        if (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
            flags |= FLAG_ENCHANTS;
        }
        if (meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)) {
            flags |= FLAG_ATTRIBUTES;
        }
        return flags;
    }

    private static void setFlag(ItemMeta meta, ItemFlag flag, boolean on) {
        if (on) {
            meta.addItemFlags(flag);
        } else {
            meta.removeItemFlags(flag);
        }
    }

    private static String friendlyName(Material material) {
        return Text.title(material.name());
    }

    private List<EnchantEntry> enchantEntries(ItemStack item) {
        final List<EnchantEntry> list = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            list.add(new EnchantEntry(entry.getKey(), enchants.getStyle(entry.getKey()), entry.getValue()));
        }
        Collections.sort(list, new Comparator<EnchantEntry>() {
            @Override
            public int compare(EnchantEntry a, EnchantEntry b) {
                final int byPriority = Integer.compare(a.style.getPriority(), b.style.getPriority());
                return byPriority != 0 ? byPriority : a.enchant.getKey().toString().compareTo(b.enchant.getKey().toString());
            }
        });
        return list;
    }

    private List<AttrEntry> attributeEntries(ItemMeta meta) {
        final List<AttrEntry> list = new ArrayList<>();
        final Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) {
            return list;
        }
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            list.add(new AttrEntry(attributes.getStyle(entry.getKey()), entry.getKey(), entry.getValue()));
        }
        Collections.sort(list, new Comparator<AttrEntry>() {
            @Override
            public int compare(AttrEntry a, AttrEntry b) {
                final int byPriority = Integer.compare(a.style.getPriority(), b.style.getPriority());
                return byPriority != 0 ? byPriority : a.attribute.getKey().toString().compareTo(b.attribute.getKey().toString());
            }
        });
        return list;
    }

    private static final class EnchantEntry {
        private final Enchantment enchant;
        private final EnchantStyle style;
        private final int level;

        private EnchantEntry(Enchantment enchant, EnchantStyle style, int level) {
            this.enchant = enchant;
            this.style = style;
            this.level = level;
        }
    }

    private static final class AttrEntry {
        private final AttributeStyle style;
        private final Attribute attribute;
        private final AttributeModifier modifier;

        private AttrEntry(AttributeStyle style, Attribute attribute, AttributeModifier modifier) {
            this.style = style;
            this.attribute = attribute;
            this.modifier = modifier;
        }
    }
}
