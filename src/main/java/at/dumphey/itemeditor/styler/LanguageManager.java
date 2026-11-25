package at.dumphey.itemeditor.styler;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads messages/&lt;language&gt;.yml. The language decides the plugin messages AND the texts written into item
 * lore (enchantment names, attribute names, and any %lang:key% used inside templates). Any language file the
 * server owner drops into plugins/UltimateItemsEditor/messages/ is picked up automatically; missing keys fall back to
 * english.
 */
public class LanguageManager {

    private static final String FALLBACK = "english";
    private static final String[] BUNDLED = {"english", "spanish"};
    private static final Pattern LANG_PLACEHOLDER = Pattern.compile("%lang:([A-Za-z0-9_.\\-]+)%");

    private final JavaPlugin plugin;
    private final StyleConfig config;
    private String activeLang = FALLBACK;
    private YamlConfiguration messages;
    private YamlConfiguration fallback;
    /** Cached: listing the messages folder is disk IO and the GUI asks for this on every render. */
    private List<String> languages = new ArrayList<>();

    public LanguageManager(JavaPlugin plugin, StyleConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        languages = scanLanguages();
        activeLang = config.getLanguage();
        fallback = loadFile(FALLBACK);
        if (FALLBACK.equals(activeLang)) {
            messages = fallback;
            return;
        }
        messages = loadFile(activeLang);
        if (messages == null) {
            plugin.getLogger().warning("Language '" + activeLang + "' not found - falling back to english.");
            activeLang = FALLBACK;
            messages = fallback;
        }
    }

    public boolean setLanguage(String language) {
        final String lang = language.toLowerCase();
        final YamlConfiguration loaded = loadFile(lang);
        if (loaded == null) {
            return false;
        }
        activeLang = lang;
        messages = loaded;
        if (fallback == null) {
            fallback = loadFile(FALLBACK);
        }
        config.setLanguage(lang);
        return true;
    }

    public String getActiveLang() {
        return activeLang;
    }

    /** Every available language: the bundled ones plus every messages/*.yml found at the last (re)load. */
    public List<String> availableLanguages() {
        return languages;
    }

    private List<String> scanLanguages() {
        final TreeSet<String> names = new TreeSet<>();
        for (String bundled : BUNDLED) {
            names.add(bundled);
        }
        final File dir = new File(plugin.getDataFolder(), "messages");
        final File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                final String n = f.getName().toLowerCase();
                if (n.endsWith(".yml")) {
                    names.add(n.substring(0, n.length() - 4));
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(names));
    }

    /** Raw (still template-formatted) text of a key; a visible marker if the key does not exist anywhere. */
    public String raw(String key) {
        final String value = lookup(key);
        return value != null ? value : "&c[Missing: " + key + "]";
    }

    /** The text of a key, or {@code fallback} when no language file has it (for names added by other code). */
    public String rawOr(String key, String fallback) {
        final String value = lookup(key);
        return value != null ? value : fallback;
    }

    /** Legacy-formatted message with the given placeholder replacements (pairs of placeholder, value). */
    public String msg(String key, String... pairs) {
        return ColorFormat.toLegacy(replace(raw(key), pairs));
    }

    /** Like {@link #msg} but with the plugin chat prefix in front. */
    public String prefixed(String key, String... pairs) {
        return ColorFormat.toLegacy(replace(raw("prefix") + raw(key), pairs));
    }

    /** Replaces every %lang:some-key% with template-text.some-key of the active language. */
    public String resolveTemplateText(String line) {
        if (line == null || line.indexOf("%lang:") < 0) {
            return line;
        }
        final Matcher m = LANG_PLACEHOLDER.matcher(line);
        final StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(raw("template-text." + m.group(1))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public String getEnchantName(String key) {
        return lookup("enchant-names." + key.toLowerCase());
    }

    public String getEnchantDescription(String key) {
        return lookup("enchant-descriptions." + key.toLowerCase());
    }

    public String getAttributeName(String key) {
        return lookup("attribute-names." + key.toLowerCase());
    }

    private String lookup(String path) {
        String value;
        if (messages != null) {
            value = messages.getString(path);
            if (value != null) {
                return value;
            }
        }
        if (fallback != null) {
            value = fallback.getString(path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String replace(String raw, String[] pairs) {
        if (pairs == null || pairs.length % 2 != 0) {
            return raw;
        }
        String result = raw;
        for (int i = 0; i < pairs.length - 1; i += 2) {
            if (pairs[i] != null && pairs[i + 1] != null) {
                result = result.replace(pairs[i], pairs[i + 1]);
            }
        }
        return result;
    }

    /** Disk overrides bundled defaults, so upgrading adds new message keys without overwriting translations. */
    private YamlConfiguration loadFile(String lang) {
        final File file = new File(plugin.getDataFolder(), "messages" + File.separator + lang + ".yml");
        final YamlConfiguration defaults = loadBundled(lang);
        if (file.exists()) {
            try {
                final YamlConfiguration configured = YamlConfiguration.loadConfiguration(file);
                if (defaults != null) configured.setDefaults(defaults);
                return configured;
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load messages/" + lang + ".yml", ex);
            }
        }
        return defaults;
    }

    private YamlConfiguration loadBundled(String lang) {
        final InputStream stream = plugin.getResource("messages/" + lang + ".yml");
        if (stream == null) return null;
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to read bundled language " + lang, ex);
            return null;
        }
    }
}
