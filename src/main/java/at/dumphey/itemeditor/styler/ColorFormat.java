package at.dumphey.itemeditor.styler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts template text into the legacy section-sign text that the Spigot item API understands.
 *
 * <p>Supported syntax (so palettes written for MiniMessage-based plugins keep working):
 * <ul>
 *   <li>legacy codes: {@code &c}, {@code &l}, {@code &r} ...</li>
 *   <li>hex colors: {@code &#RRGGBB}, {@code <#RRGGBB>}, {@code <color:#RRGGBB>}</li>
 *   <li>named colors: {@code <red>}, {@code <dark_gray>} ...</li>
 *   <li>decorations: {@code <bold>}, {@code <italic>}, {@code <underlined>}, {@code <strikethrough>},
 *       {@code <obfuscated>} (and b, i, u, st, obf), negated with {@code <!bold>}</li>
 *   <li>{@code <reset>}, closing tags such as {@code </red>}</li>
 *   <li>{@code <gradient:#ff0000:#0000ff>...</gradient>} and {@code <rainbow>...</rainbow>}</li>
 * </ul>
 * {@code &} codes behave like vanilla legacy chat: a color code ({@code &c}, {@code &#RRGGBB}) clears the
 * decorations before it. {@code <tags>} behave like MiniMessage: {@code <red>} keeps bold/italic until the matching
 * closing tag or {@code <reset>}. Unknown {@code <tags>} are kept as plain text.
 */
public final class ColorFormat {

    private static final char SECTION = '\u00a7';

    private static final int T_CHAR = 0;
    private static final int T_COLOR = 1;
    private static final int T_DECO = 2;
    private static final int T_NEG = 3;
    private static final int T_RESET = 4;
    private static final int T_GRADIENT = 5;
    private static final int T_RAINBOW = 6;
    private static final int T_CLOSE = 7;

    private static final int BOLD = 1;
    private static final int ITALIC = 2;
    private static final int UNDERLINED = 4;
    private static final int STRIKETHROUGH = 8;
    private static final int OBFUSCATED = 16;

    private static final String LEGACY_CHARS = "0123456789abcdef";
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
    private static final String[] LEGACY_NAMES = {
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};

    private static final Map<String, Integer> NAME_TO_INDEX = new HashMap<>();

    static {
        for (int i = 0; i < LEGACY_NAMES.length; i++) {
            NAME_TO_INDEX.put(LEGACY_NAMES[i], i);
        }
        NAME_TO_INDEX.put("grey", 7);
        NAME_TO_INDEX.put("dark_grey", 8);
    }

    private ColorFormat() {
    }

    private static final class Token {
        private int type;
        private boolean ampersand; // T_COLOR written with & (legacy semantics: clears decorations)
        private String text;      // T_CHAR: the visible character (a surrogate pair counts as one)
        private String name;      // canonical tag name (used to match closing tags)
        private int rgb;          // T_COLOR
        private char legacy;      // T_COLOR: legacy code char, or 0 for a hex color
        private int[] colors;     // T_GRADIENT
        private int length;       // T_GRADIENT / T_RAINBOW: number of visible characters covered

        private Token(int type) {
            this.type = type;
        }
    }

    private static final class Entry {
        private final Token token;
        private final int start;

        private Entry(Token token, int start) {
            this.token = token;
            this.start = start;
        }
    }

    /** Converts template text to legacy section-sign text. Never returns null. */
    public static String toLegacy(String input) {
        return convert(input, false);
    }

    /**
     * Like {@link #toLegacy} for text that is placed IN FRONT of other text (e.g. a display-name prefix such as
     * "&6&l"): the trailing, still-open color and decorations are written out so they style what follows.
     */
    public static String toLegacyPrefix(String input) {
        return convert(input, true);
    }

    private static String convert(String input, boolean flushOpenStyle) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        final List<Token> tokens = tokenize(input);
        if (flushOpenStyle) {
            final Token end = new Token(T_CHAR);
            end.text = "";
            tokens.add(end);
        }
        final List<Entry> stack = new ArrayList<>();
        final StringBuilder sb = new StringBuilder(input.length() + 16);

        int charIndex = 0;
        int lastRgb = -1;
        int lastFlags = 0;

        for (Token t : tokens) {
            switch (t.type) {
                case T_RESET:
                    stack.clear();
                    break;
                case T_COLOR:
                    if (t.ampersand) {
                        for (int k = stack.size() - 1; k >= 0; k--) {
                            final int type = stack.get(k).token.type;
                            if (type == T_DECO || type == T_NEG) {
                                stack.remove(k);
                            }
                        }
                    }
                    stack.add(new Entry(t, charIndex));
                    break;
                case T_DECO:
                case T_NEG:
                case T_GRADIENT:
                case T_RAINBOW:
                    stack.add(new Entry(t, charIndex));
                    break;
                case T_CLOSE:
                    close(stack, t.name);
                    break;
                default: // T_CHAR
                    int rgb = -1;
                    char legacy = 0;
                    for (int k = stack.size() - 1; k >= 0; k--) {
                        final Entry e = stack.get(k);
                        if (e.token.type == T_COLOR) {
                            rgb = e.token.rgb;
                            legacy = e.token.legacy;
                            break;
                        } else if (e.token.type == T_GRADIENT) {
                            rgb = gradient(e.token.colors, charIndex - e.start, e.token.length);
                            break;
                        } else if (e.token.type == T_RAINBOW) {
                            rgb = rainbow(charIndex - e.start, e.token.length);
                            break;
                        }
                    }
                    int flags = 0;
                    for (Entry e : stack) {
                        if (e.token.type == T_DECO) {
                            flags |= flagOf(e.token.name);
                        } else if (e.token.type == T_NEG) {
                            flags &= ~flagOf(e.token.name);
                        }
                    }

                    final boolean visibleStyle = (flags & (UNDERLINED | STRIKETHROUGH)) != 0;
                    if (" ".equals(t.text) && !visibleStyle) {
                        sb.append(' '); // spaces carry no color: avoid pointless codes
                        charIndex++;
                        break;
                    }

                    if (rgb != lastRgb || flags != lastFlags) {
                        final boolean reset = lastFlags != 0 || (rgb == -1 && lastRgb != -1);
                        int baseRgb = lastRgb;
                        if (reset) {
                            sb.append(SECTION).append('r');
                            baseRgb = -1;
                        }
                        if (rgb != -1 && rgb != baseRgb) {
                            appendColor(sb, rgb, legacy);
                        }
                        appendDecorations(sb, flags);
                        lastRgb = rgb;
                        lastFlags = flags;
                    }
                    sb.append(t.text);
                    charIndex++;
                    break;
            }
        }
        return sb.toString();
    }

    /** Text without any color/format information (accepts both template syntax and section signs). */
    public static String strip(String input) {
        final String legacy = toLegacy(input);
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < legacy.length(); i++) {
            final char c = legacy.charAt(i);
            if (c == SECTION && i + 1 < legacy.length()) {
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ tokenizer

    private static List<Token> tokenize(String s) {
        final List<Token> out = new ArrayList<>();
        final int n = s.length();
        int i = 0;
        while (i < n) {
            final char c = s.charAt(i);
            if (c == '&' && i + 1 < n) {
                final char d = s.charAt(i + 1);
                if (d == '#' && i + 8 <= n && isHex(s, i + 2, 6)) {
                    final Token hex = colorToken(Integer.parseInt(s.substring(i + 2, i + 8), 16), (char) 0, "color");
                    hex.ampersand = true;
                    out.add(hex);
                    i += 8;
                    continue;
                }
                final Token legacy = legacyToken(Character.toLowerCase(d));
                if (legacy != null) {
                    out.add(legacy);
                    i += 2;
                    continue;
                }
            } else if (c == '<') {
                final int end = s.indexOf('>', i + 1);
                if (end > i + 1) {
                    final Token tag = parseTag(s.substring(i + 1, end));
                    if (tag != null) {
                        out.add(tag);
                        i = end + 1;
                        continue;
                    }
                }
            }
            final Token ch = new Token(T_CHAR);
            if (Character.isHighSurrogate(c) && i + 1 < n && Character.isLowSurrogate(s.charAt(i + 1))) {
                ch.text = s.substring(i, i + 2);
                i += 2;
            } else {
                ch.text = String.valueOf(c);
                i++;
            }
            out.add(ch);
        }
        computeLengths(out);
        return out;
    }

    private static void computeLengths(List<Token> tokens) {
        for (int a = 0; a < tokens.size(); a++) {
            final Token open = tokens.get(a);
            if (open.type != T_GRADIENT && open.type != T_RAINBOW) {
                continue;
            }
            int depth = 1;
            int count = 0;
            for (int b = a + 1; b < tokens.size(); b++) {
                final Token t = tokens.get(b);
                if (t.type == T_RESET) {
                    break;
                }
                if (t.type == open.type) {
                    depth++;
                } else if (t.type == T_CLOSE && open.name.equals(t.name)) {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                } else if (t.type == T_CHAR) {
                    count++;
                }
            }
            open.length = count;
        }
    }

    private static Token legacyToken(char d) {
        final int idx = LEGACY_CHARS.indexOf(d);
        if (idx >= 0) {
            final Token color = colorToken(LEGACY_RGB[idx], d, "color");
            color.ampersand = true;
            return color;
        }
        switch (d) {
            case 'k':
                return decoToken(T_DECO, "obfuscated");
            case 'l':
                return decoToken(T_DECO, "bold");
            case 'm':
                return decoToken(T_DECO, "strikethrough");
            case 'n':
                return decoToken(T_DECO, "underlined");
            case 'o':
                return decoToken(T_DECO, "italic");
            case 'r':
                return new Token(T_RESET);
            default:
                return null;
        }
    }

    private static Token parseTag(String raw) {
        final boolean closing = raw.startsWith("/");
        String body = closing ? raw.substring(1) : raw;
        final boolean negated = !closing && body.startsWith("!");
        if (negated) {
            body = body.substring(1);
        }
        final String[] parts = body.split(":", -1);
        final String name = parts[0].toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.indexOf(' ') >= 0) {
            return null; // "a < b > c" is plain text, not a tag
        }

        final String deco = decoName(name);
        if (deco != null) {
            if (closing) {
                return closeToken(deco);
            }
            return decoToken(negated ? T_NEG : T_DECO, deco);
        }

        if (negated) {
            return null;
        }

        if (name.equals("reset")) {
            return closing ? null : new Token(T_RESET);
        }

        if (name.equals("rainbow")) {
            if (closing) {
                return closeToken("rainbow");
            }
            final Token t = new Token(T_RAINBOW);
            t.name = "rainbow";
            return t;
        }

        if (name.equals("gradient")) {
            if (closing) {
                return closeToken("gradient");
            }
            final List<Integer> colors = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                final Integer rgb = parseColorSpec(parts[i]);
                if (rgb != null) {
                    colors.add(rgb);
                } else if (!isNumber(parts[i])) {
                    return null;
                }
            }
            if (colors.isEmpty()) {
                colors.add(0xFFFFFF);
                colors.add(0x000000);
            } else if (colors.size() == 1) {
                colors.add(colors.get(0));
            }
            final Token t = new Token(T_GRADIENT);
            t.name = "gradient";
            t.colors = new int[colors.size()];
            for (int i = 0; i < t.colors.length; i++) {
                t.colors[i] = colors.get(i);
            }
            return t;
        }

        if (name.equals("color") || name.equals("colour") || name.equals("c")) {
            if (closing) {
                return closeToken("color");
            }
            if (parts.length < 2) {
                return null;
            }
            return colorFromSpec(parts[1], "color");
        }

        if (name.startsWith("#")) {
            return closing ? closeToken("color") : colorFromSpec(name, "color");
        }

        final Integer index = NAME_TO_INDEX.get(name);
        if (index != null) {
            return closing ? closeToken(LEGACY_NAMES[index]) : colorToken(LEGACY_RGB[index], LEGACY_CHARS.charAt(index), LEGACY_NAMES[index]);
        }
        return null;
    }

    private static Token colorFromSpec(String spec, String name) {
        final String s = spec.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("#") && s.length() == 7 && isHex(s, 1, 6)) {
            return colorToken(Integer.parseInt(s.substring(1), 16), (char) 0, name);
        }
        final Integer index = NAME_TO_INDEX.get(s);
        if (index != null) {
            return colorToken(LEGACY_RGB[index], LEGACY_CHARS.charAt(index), name);
        }
        return null;
    }

    private static Integer parseColorSpec(String spec) {
        final Token t = colorFromSpec(spec, "color");
        return t == null ? null : Integer.valueOf(t.rgb);
    }

    private static String decoName(String name) {
        switch (name) {
            case "bold":
            case "b":
                return "bold";
            case "italic":
            case "i":
            case "em":
                return "italic";
            case "underlined":
            case "u":
                return "underlined";
            case "strikethrough":
            case "st":
                return "strikethrough";
            case "obfuscated":
            case "obf":
                return "obfuscated";
            default:
                return null;
        }
    }

    private static int flagOf(String deco) {
        switch (deco) {
            case "bold":
                return BOLD;
            case "italic":
                return ITALIC;
            case "underlined":
                return UNDERLINED;
            case "strikethrough":
                return STRIKETHROUGH;
            default:
                return OBFUSCATED;
        }
    }

    private static Token colorToken(int rgb, char legacy, String name) {
        final Token t = new Token(T_COLOR);
        t.rgb = rgb;
        t.legacy = legacy;
        t.name = name;
        return t;
    }

    private static Token decoToken(int type, String name) {
        final Token t = new Token(type);
        t.name = name;
        return t;
    }

    private static Token closeToken(String name) {
        final Token t = new Token(T_CLOSE);
        t.name = name;
        return t;
    }

    private static boolean isHex(String s, int from, int count) {
        if (from + count > s.length()) {
            return false;
        }
        for (int i = from; i < from + count; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ rendering helpers

    private static void close(List<Entry> stack, String name) {
        for (int i = stack.size() - 1; i >= 0; i--) {
            if (name.equals(stack.get(i).token.name)) {
                while (stack.size() > i) {
                    stack.remove(stack.size() - 1); // also closes whatever was opened inside it
                }
                return;
            }
        }
    }

    private static void appendColor(StringBuilder sb, int rgb, char legacy) {
        if (legacy != 0) {
            sb.append(SECTION).append(legacy);
            return;
        }
        sb.append(SECTION).append('x');
        final String hex = String.format("%06x", rgb & 0xFFFFFF);
        for (int i = 0; i < hex.length(); i++) {
            sb.append(SECTION).append(hex.charAt(i));
        }
    }

    private static void appendDecorations(StringBuilder sb, int flags) {
        if ((flags & BOLD) != 0) {
            sb.append(SECTION).append('l');
        }
        if ((flags & ITALIC) != 0) {
            sb.append(SECTION).append('o');
        }
        if ((flags & UNDERLINED) != 0) {
            sb.append(SECTION).append('n');
        }
        if ((flags & STRIKETHROUGH) != 0) {
            sb.append(SECTION).append('m');
        }
        if ((flags & OBFUSCATED) != 0) {
            sb.append(SECTION).append('k');
        }
    }

    private static int gradient(int[] colors, int pos, int length) {
        if (colors.length == 1 || length <= 1) {
            return colors[0];
        }
        final double t = Math.min(1.0, Math.max(0.0, (double) pos / (length - 1)));
        final double scaled = t * (colors.length - 1);
        final int idx = Math.min((int) scaled, colors.length - 2);
        final double f = scaled - idx;
        final int a = colors[idx];
        final int b = colors[idx + 1];
        final int r = (int) Math.round(((a >> 16) & 0xFF) + f * (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)));
        final int g = (int) Math.round(((a >> 8) & 0xFF) + f * (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)));
        final int bl = (int) Math.round((a & 0xFF) + f * ((b & 0xFF) - (a & 0xFF)));
        return (r << 16) | (g << 8) | bl;
    }

    private static int rainbow(int pos, int length) {
        final double hue = length <= 0 ? 0 : (double) pos / length;
        final double h = hue * 6.0;
        final int sector = ((int) Math.floor(h)) % 6;
        final double f = h - Math.floor(h);
        final double q = 1.0 - f;
        double r;
        double g;
        double b;
        switch (sector) {
            case 0:
                r = 1;
                g = f;
                b = 0;
                break;
            case 1:
                r = q;
                g = 1;
                b = 0;
                break;
            case 2:
                r = 0;
                g = 1;
                b = f;
                break;
            case 3:
                r = 0;
                g = q;
                b = 1;
                break;
            case 4:
                r = f;
                g = 0;
                b = 1;
                break;
            default:
                r = 1;
                g = 0;
                b = q;
                break;
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }
}
