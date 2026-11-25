package at.dumphey.itemeditor.commands;

import at.dumphey.itemeditor.engine.EnchantCatalog;
import at.dumphey.itemeditor.engine.ItemTagData;
import at.dumphey.itemeditor.engine.ItemTags;
import at.dumphey.itemeditor.engine.Rule;
import at.dumphey.itemeditor.engine.Slots;
import at.dumphey.itemeditor.engine.TriggerEngine;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.styler.LanguageManager;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** /ie rules ... and /ie tag ... */
public class RuleCommands {

    private static final List<String> RULES_SUB = Arrays.asList("list", "info", "reload", "errors", "stats");
    private static final List<String> TAG_SUB = Arrays.asList("item", "enchant", "info");

    private TriggerEngine engine() {
        return ItemEditor.INSTANCE.getTriggerEngine();
    }

    private LanguageManager lang() {
        return ItemEditor.INSTANCE.getStyleService().getLang();
    }

    // ------------------------------------------------------------------ /ie rules

    public void handleRules(CommandSender sender, String[] args) {
        final LanguageManager lang = lang();
        final TriggerEngine engine = engine();
        if (args.length < 2) {
            sender.sendMessage(lang.prefixed("rules-usage"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "list":
                list(sender, engine, lang);
                break;
            case "info":
                info(sender, engine, lang, args);
                break;
            case "reload":
                engine.plugin().reloadConfig();
                engine.load();
                if (!engine.isEnabled()) {
                    sender.sendMessage(lang.prefixed("rules-disabled-config"));
                    break;
                }
                sender.sendMessage(lang.prefixed("rules-reloaded", "%rules%", String.valueOf(engine.getRules().size()),
                        "%disabled%", String.valueOf(engine.getDisabledInFiles()), "%errors%", String.valueOf(engine.getErrors().size()),
                        "%listeners%", String.valueOf(engine.getListenerCount())));
                if (!engine.getErrors().isEmpty()) {
                    errors(sender, engine, lang);
                }
                break;
            case "errors":
                errors(sender, engine, lang);
                break;
            case "stats":
                sender.sendMessage(lang.prefixed("rules-stats", "%events%", String.valueOf(engine.getEventsHandled()),
                        "%evaluated%", String.valueOf(engine.getRulesEvaluated()), "%fired%", String.valueOf(engine.getRulesFired()),
                        "%errors%", String.valueOf(engine.getRuleErrors()), "%listeners%", String.valueOf(engine.getListenerCount()),
                        "%periodic%", String.valueOf(engine.isPeriodicRunning())));
                if (engine.enchantCatalog() != null) {
                    sender.sendMessage(lang.prefixed("catalog-performance-stats", "%discarded%",
                            Long.toString(engine.enchantCatalog().discardedWork())));
                }
                break;
            default:
                sender.sendMessage(lang.prefixed("rules-usage"));
                break;
        }
    }

    private void list(CommandSender sender, TriggerEngine engine, LanguageManager lang) {
        if (engine.getRules().isEmpty()) {
            sender.sendMessage(lang.prefixed("rules-none"));
            return;
        }
        final List<String> names = new ArrayList<>();
        for (Rule rule : engine.getRules()) {
            names.add((rule.isDisabled() ? "\u00a7c" : "") + rule.getId() + "\u00a78(" + rule.getTrigger().name().toLowerCase() + ")\u00a77");
        }
        sender.sendMessage(lang.prefixed("rules-list", "%count%", String.valueOf(names.size()), "%list%", String.join(", ", names)));
    }

    private void info(CommandSender sender, TriggerEngine engine, LanguageManager lang, String[] args) {
        final Rule rule = args.length > 2 ? engine.getRule(args[2]) : null;
        if (rule == null) {
            sender.sendMessage(lang.prefixed("rules-not-found", "%rule%", args.length > 2 ? args[2] : ""));
            return;
        }
        final String bound = rule.getItemId() != null ? "item " + rule.getItemId()
                : rule.getEnchantId() != null ? "enchant " + rule.getEnchantId() : "-";
        sender.sendMessage(lang.msg("rules-info-header", "%rule%", rule.getId()));
        sender.sendMessage(lang.msg("rules-info-trigger", "%trigger%", rule.getTrigger().name(), "%bound%", bound,
                "%slots%", slotNames(rule.getSlotMask())));
        sender.sendMessage(lang.msg("rules-info-run", "%cooldown%", rule.getCooldownMs() == 0 ? "-" : (rule.getCooldownMs() / 1000.0) + "s",
                "%fired%", String.valueOf(rule.getFired()), "%errors%", String.valueOf(rule.getErrors()),
                "%state%", rule.isDisabled() ? "\u00a7c(switched off)" : ""));
        sender.sendMessage(lang.msg("rules-info-file", "%file%", rule.getSource()));
    }

    private void errors(CommandSender sender, TriggerEngine engine, LanguageManager lang) {
        final List<String> errors = engine.getErrors();
        if (errors.isEmpty()) {
            sender.sendMessage(lang.prefixed("rules-errors-none"));
            return;
        }
        sender.sendMessage(lang.prefixed("rules-errors-header", "%count%", String.valueOf(errors.size())));
        for (int i = 0; i < Math.min(10, errors.size()); i++) {
            sender.sendMessage("\u00a7c- \u00a77" + errors.get(i));
        }
        if (errors.size() > 10) {
            sender.sendMessage("\u00a78... " + (errors.size() - 10) + " more in the console");
        }
    }

    private static String slotNames(int mask) {
        if (mask == Slots.ANY) {
            return "any";
        }
        final List<String> names = new ArrayList<>();
        final String[] all = {"hand", "off_hand", "feet", "legs", "chest", "head"};
        for (int i = 0; i < all.length; i++) {
            if ((mask & (1 << i)) != 0) {
                names.add(all[i]);
            }
        }
        return String.join(",", names);
    }

    // ------------------------------------------------------------------ /ie tag

    public void handleTag(CommandSender sender, String[] args) {
        final LanguageManager lang = lang();
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.prefixed("player-only"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(lang.prefixed("tag-usage"));
            return;
        }
        final Player player = (Player) sender;
        final ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR || item.getItemMeta() == null) {
            player.sendMessage(lang.prefixed("no-item-in-hand"));
            return;
        }
        final ItemTags tags = engine().tags();
        final ItemMeta meta = item.getItemMeta();

        switch (args[1].toLowerCase()) {
            case "item":
                if (args.length < 3) {
                    player.sendMessage(lang.prefixed("tag-usage"));
                    return;
                }
                if (args[2].equalsIgnoreCase("clear")) {
                    tags.setItemId(meta, null);
                    commit(player, item, meta);
                    player.sendMessage(lang.prefixed("tag-item-cleared"));
                } else if (!ItemTags.isValidId(ItemTags.normalize(args[2]))) {
                    player.sendMessage(lang.prefixed("tag-invalid-id", "%id%", args[2]));
                } else {
                    tags.setItemId(meta, args[2]);
                    commit(player, item, meta);
                    player.sendMessage(lang.prefixed("tag-item-set", "%id%", ItemTags.normalize(args[2])));
                }
                break;
            case "enchant":
                if (args.length < 4) {
                    player.sendMessage(lang.prefixed("tag-usage"));
                    return;
                }
                if (!ItemTags.isValidId(ItemTags.normalize(args[2]))) {
                    player.sendMessage(lang.prefixed("tag-invalid-id", "%id%", args[2]));
                    return;
                }
                final int level;
                try {
                    level = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    player.sendMessage(lang.prefixed("tag-invalid-level"));
                    return;
                }
                if (level < 0 || level > 255) {
                    player.sendMessage(lang.prefixed("tag-invalid-level"));
                    return;
                }
                final String enchantId = ItemTags.normalize(args[2]);
                final EnchantCatalog catalog = engine().enchantCatalog();
                if (catalog != null && catalog.get(enchantId) != null) {
                    if (!catalog.apply(item, enchantId, level)) {
                        player.sendMessage(lang.prefixed("catalog-incompatible"));
                        return;
                    }
                    player.getInventory().setItemInMainHand(item);
                } else {
                    tags.setEnchant(meta, enchantId, level);
                    commit(player, item, meta);
                }
                player.sendMessage(level == 0
                        ? lang.prefixed("tag-enchant-removed", "%id%", ItemTags.normalize(args[2]))
                        : lang.prefixed("tag-enchant-set", "%id%", ItemTags.normalize(args[2]), "%level%", String.valueOf(level)));
                break;
            case "info":
                final ItemTagData data = tags.read(meta);
                if (data == null) {
                    player.sendMessage(lang.prefixed("tag-info-none"));
                    return;
                }
                final List<String> enchants = new ArrayList<>();
                for (Map.Entry<String, Integer> e : data.getEnchants().entrySet()) {
                    enchants.add(e.getKey() + " " + e.getValue());
                }
                player.sendMessage(lang.prefixed("tag-info", "%item%", data.getItemId() == null ? "-" : data.getItemId(),
                        "%enchants%", enchants.isEmpty() ? "-" : String.join(", ", enchants)));
                break;
            default:
                player.sendMessage(lang.prefixed("tag-usage"));
                break;
        }
    }

    private static void commit(Player player, ItemStack item, ItemMeta meta) {
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
    }

    // ------------------------------------------------------------------ tab completion

    public List<String> tabRules(String[] args) {
        if (args.length == 2) {
            return filter(RULES_SUB, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("info")) {
            final List<String> ids = new ArrayList<>();
            for (Rule r : engine().getRules()) {
                ids.add(r.getId());
            }
            return filter(ids, args[2]);
        }
        return new ArrayList<>();
    }

    public List<String> tabTag(String[] args) {
        if (args.length == 2) {
            final List<String> options = new ArrayList<>(TAG_SUB);
            options.removeIf(option -> ItemEditor.INSTANCE.modules().disabledCommand("tag", option) != null);
            return filter(options, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("item")) {
            return filter(Arrays.asList("clear"), args[2]);
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        final String lower = prefix.toLowerCase();
        final List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
