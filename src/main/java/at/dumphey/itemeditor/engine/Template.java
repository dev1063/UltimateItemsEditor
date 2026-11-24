package at.dumphey.itemeditor.engine;

import at.dumphey.itemeditor.styler.ColorFormat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A text with %placeholders%, parsed once. Colors are converted at load time; at run time only the placeholders
 * are filled in.
 * Placeholders: %player% %target% %world% %level% %damage% %item% %x% %y% %z% %rule% %trigger% %click% %cooldown%
 */
public final class Template {

    private static final List<String> NAMES = Arrays.asList("player", "target", "world", "level", "damage", "item",
            "x", "y", "z", "rule", "trigger", "click", "cooldown");

    private final String[] literals;
    private final int[] tokens; // tokens[i] is the placeholder written after literals[i]; -1 = none

    private Template(String[] literals, int[] tokens) {
        this.literals = literals;
        this.tokens = tokens;
    }

    public static Template compile(String text, boolean colors) {
        final String source = colors ? ColorFormat.toLegacy(text) : text;
        final List<String> lits = new ArrayList<>();
        final List<Integer> toks = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            final char c = source.charAt(i);
            if (c == '%') {
                final int end = source.indexOf('%', i + 1);
                if (end > i + 1) {
                    final int token = NAMES.indexOf(source.substring(i + 1, end).toLowerCase());
                    if (token >= 0) {
                        lits.add(current.toString());
                        toks.add(token);
                        current.setLength(0);
                        i = end + 1;
                        continue;
                    }
                }
            }
            current.append(c);
            i++;
        }
        lits.add(current.toString());
        toks.add(-1);
        final int[] tokenArray = new int[toks.size()];
        for (int k = 0; k < tokenArray.length; k++) {
            tokenArray[k] = toks.get(k);
        }
        return new Template(lits.toArray(new String[0]), tokenArray);
    }

    public boolean isConstant() {
        return literals.length == 1;
    }

    public String render(TriggerContext ctx) {
        if (literals.length == 1) {
            return literals[0];
        }
        final StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < literals.length; i++) {
            sb.append(literals[i]);
            if (tokens[i] >= 0) {
                sb.append(value(tokens[i], ctx));
            }
        }
        return sb.toString();
    }

    private static String value(int token, TriggerContext ctx) {
        switch (token) {
            case 0:
                return ctx.player == null ? "" : ctx.player.getName();
            case 1:
                return targetName(ctx);
            case 2:
                return ctx.location() == null || ctx.location().getWorld() == null ? "" : ctx.location().getWorld().getName();
            case 3:
                return String.valueOf(ctx.level);
            case 4:
                return trim(ctx.getDamage());
            case 5:
                return ctx.itemId != null ? ctx.itemId : (ctx.item == null ? "" : ctx.item.getType().name());
            case 6:
                return ctx.location() == null ? "" : String.valueOf(ctx.location().getBlockX());
            case 7:
                return ctx.location() == null ? "" : String.valueOf(ctx.location().getBlockY());
            case 8:
                return ctx.location() == null ? "" : String.valueOf(ctx.location().getBlockZ());
            case 9:
                return ctx.rule == null ? "" : ctx.rule.getId();
            case 10:
                return ctx.type == null ? "" : ctx.type.name();
            case 11:
                return ctx.click == null ? "" : ctx.click.name();
            default: // cooldown: seconds left
                return String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownLeftMs / 1000.0);
        }
    }

    private static String targetName(TriggerContext ctx) {
        if (ctx.target == null) {
            return "";
        }
        if (ctx.target instanceof org.bukkit.entity.Player) {
            return ((org.bukkit.entity.Player) ctx.target).getName();
        }
        return ctx.target.getType().name();
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
