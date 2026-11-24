package at.dumphey.itemeditor.engine;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Helpers to read the loosely typed values a YAML file gives us. */
public final class Cfg {

    private Cfg() {
    }

    /** Bukkit hands out ConfigurationSections for some maps and plain Maps for others: make everything plain. */
    public static Object plain(Object o) {
        if (o instanceof ConfigurationSection) {
            final ConfigurationSection section = (ConfigurationSection) o;
            final Map<String, Object> map = new LinkedHashMap<>();
            for (String key : section.getKeys(false)) {
                map.put(key, plain(section.get(key)));
            }
            return map;
        }
        if (o instanceof Map) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                map.put(String.valueOf(e.getKey()), plain(e.getValue()));
            }
            return map;
        }
        if (o instanceof List) {
            final List<Object> list = new ArrayList<>();
            for (Object e : (List<?>) o) {
                list.add(plain(e));
            }
            return list;
        }
        return o;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : null;
    }

    /** null -> empty list, a list -> itself, anything else -> a list with that one value. */
    @SuppressWarnings("unchecked")
    public static List<Object> list(Object o) {
        final List<Object> out = new ArrayList<>();
        if (o == null) {
            return out;
        }
        if (o instanceof List) {
            out.addAll((List<Object>) o);
        } else {
            out.add(o);
        }
        return out;
    }

    public static double number(Object o, ParseContext pc) throws ConfigException {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            throw pc.error("'" + o + "' is not a number");
        }
    }

    public static boolean bool(Object o, ParseContext pc) throws ConfigException {
        if (o instanceof Boolean) {
            return (Boolean) o;
        }
        final String s = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
        if (s.equals("true") || s.equals("yes") || s.equals("on")) {
            return true;
        }
        if (s.equals("false") || s.equals("no") || s.equals("off")) {
            return false;
        }
        throw pc.error("'" + o + "' is not true or false");
    }

    /** Upper-cased names (Material, EntityType, GameMode...). */
    public static Set<String> upperSet(Object o) {
        final Set<String> set = new TreeSet<>();
        for (Object e : list(o)) {
            set.add(String.valueOf(e).trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        }
        return set;
    }

    /** Rejects keys that are not in {@code allowed}: a typo must be an error, not a silently ignored option. */
    public static void checkKeys(Map<String, Object> map, ParseContext pc, String... allowed) throws ConfigException {
        for (String key : map.keySet()) {
            boolean ok = false;
            for (String a : allowed) {
                if (a.equalsIgnoreCase(key)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw pc.error("unknown option '" + key + "' (allowed: " + String.join(", ", allowed) + ")");
            }
        }
    }

    /** Case-insensitive set of names. */
    public static Set<String> nameSet(Object o) {
        final Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Object e : list(o)) {
            set.add(String.valueOf(e).trim());
        }
        return set;
    }

    public static Set<String> lowerSet(Object o) {
        final Set<String> set = new TreeSet<>();
        for (Object e : list(o)) {
            set.add(String.valueOf(e).trim().toLowerCase(Locale.ROOT));
        }
        return set;
    }
}
