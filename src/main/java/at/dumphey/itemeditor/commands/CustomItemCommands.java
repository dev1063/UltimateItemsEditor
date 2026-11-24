package at.dumphey.itemeditor.commands;

import at.dumphey.itemeditor.engine.ConfigException;
import at.dumphey.itemeditor.engine.CustomItem;
import at.dumphey.itemeditor.engine.CustomItemManager;
import at.dumphey.itemeditor.engine.Rule;
import at.dumphey.itemeditor.engine.TriggerEngine;
import at.dumphey.itemeditor.engine.TriggerType;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.storage.SerializedItem;
import at.dumphey.itemeditor.styler.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

/** /ie customitem ... : items with skills. */
public class CustomItemCommands {

    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "update", "give", "info", "list", "delete",
            "skill", "reload", "errors");
    private static final List<String> WITH_ID = Arrays.asList("update", "give", "info", "delete", "skill");
    private static final List<String> SKILL_SUB = Arrays.asList("add", "remove", "action", "condition", "delaction",
            "delcond", "set");

    private TriggerEngine engine() {
        return ItemEditor.INSTANCE.getTriggerEngine();
    }

    private CustomItemManager manager() {
        return engine().customItems();
    }

    private LanguageManager lang() {
        return ItemEditor.INSTANCE.getStyleService().getLang();
    }

    public void handle(CommandSender sender, String[] args) {
        final LanguageManager lang = lang();
        final CustomItemManager manager = manager();
        if (args.length < 2) {
            sender.sendMessage(lang.prefixed("customitem-usage"));
            return;
        }
        final String sub = args[1].toLowerCase(Locale.ROOT);
        if (!manager.isEnabled() && !sub.equals("reload")) {
            sender.sendMessage(lang.prefixed("customitem-disabled"));
            return;
        }
        try {
            switch (sub) {
                case "list":
                    list(sender, manager, lang);
                    break;
                case "info":
                    info(sender, manager, lang, args);
                    break;
                case "create":
                    createOrUpdate(sender, manager, lang, args, true);
                    break;
                case "update":
                    createOrUpdate(sender, manager, lang, args, false);
                    break;
                case "delete":
                    delete(sender, manager, lang, args);
                    break;
                case "give":
                    give(sender, manager, lang, args);
                    break;
                case "skill":
                    skill(sender, manager, lang, args);
                    break;
                case "reload":
                    engine().plugin().reloadConfig();
                    engine().load();
                    sender.sendMessage(lang.prefixed("customitem-reloaded", "%count%", String.valueOf(manager.all().size()),
                            "%errors%", String.valueOf(manager.getErrors().size())));
                    if (!manager.getErrors().isEmpty()) {
                        errors(sender, manager, lang);
                    }
                    break;
                case "errors":
                    errors(sender, manager, lang);
                    break;
                default:
                    sender.sendMessage(lang.prefixed("customitem-usage"));
                    break;
            }
        } catch (ConfigException e) {
            sender.sendMessage(lang.prefixed("customitem-invalid", "%error%", e.getMessage()));
        } catch (IOException e) {
            engine().plugin().getLogger().log(Level.SEVERE, "Could not save a custom item", e);
            sender.sendMessage(lang.prefixed("customitem-save-failed"));
        }
    }

    private void list(CommandSender sender, CustomItemManager manager, LanguageManager lang) {
        if (manager.all().isEmpty()) {
            sender.sendMessage(lang.prefixed("customitem-none"));
            return;
        }
        final List<String> ids = new ArrayList<>();
        for (CustomItem item : manager.all()) {
            ids.add(item.getId() + "\u00a78(" + item.getRules().size() + ")\u00a77");
        }
        sender.sendMessage(lang.prefixed("customitem-list", "%count%", String.valueOf(ids.size()), "%list%", String.join(", ", ids)));
    }

    private CustomItem find(CommandSender sender, CustomItemManager manager, LanguageManager lang, String[] args, int index) {
        final CustomItem item = args.length > index ? manager.get(args[index]) : null;
        if (item == null) {
            sender.sendMessage(lang.prefixed("customitem-not-found", "%id%", args.length > index ? args[index] : ""));
        }
        return item;
    }

    private void info(CommandSender sender, CustomItemManager manager, LanguageManager lang, String[] args) {
        final CustomItem item = find(sender, manager, lang, args, 2);
        if (item == null) {
            return;
        }
        sender.sendMessage(lang.msg("customitem-info-header", "%id%", item.getId()));
        sender.sendMessage(lang.msg("customitem-info-item", "%material%", item.getTemplate().getType().name(),
                "%count%", String.valueOf(item.getRules().size()), "%disabled%", String.valueOf(item.getDisabledSkills())));
        if (item.getSkillIds().isEmpty()) {
            sender.sendMessage(lang.msg("customitem-info-noskills"));
            return;
        }
        for (Rule rule : item.getRules()) {
            final String skill = rule.getId().substring(item.getId().length() + 1);
            sender.sendMessage(lang.msg("customitem-info-skill", "%skill%", rule.getDisplayName() != null ? skill + " \u00a77(" + rule.getDisplayName() + ")" : skill,
                    "%trigger%", rule.getTrigger().name().toLowerCase(Locale.ROOT),
                    "%cooldown%", rule.getCooldownMs() == 0 ? "-" : (rule.getCooldownMs() / 1000.0) + "s",
                    "%actions%", String.valueOf(rule.getActionCount()),
                    "%fired%", String.valueOf(rule.getFired()), "%errors%", String.valueOf(rule.getErrors()),
                    "%state%", rule.isDisabled() ? "\u00a7c(switched off)" : ""));
        }
        if (item.getSkillIds().size() > item.getRules().size() + item.getDisabledSkills()) {
            sender.sendMessage(lang.msg("customitem-info-broken"));
        }
    }

    private ItemStack sourceItem(CommandSender sender, LanguageManager lang, String[] args) {
        if (args.length > 3 && args[3].equalsIgnoreCase("from")) {
            final SerializedItem saved = args.length > 4 ? ItemEditor.INSTANCE.getItemStorage().getItem(args[4]) : null;
            if (saved == null) {
                sender.sendMessage(lang.prefixed("customitem-saved-not-found", "%name%", args.length > 4 ? args[4] : ""));
                return null;
            }
            return saved.getItemStack();
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.prefixed("player-only"));
            return null;
        }
        final ItemStack hand = ((Player) sender).getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            sender.sendMessage(lang.prefixed("no-item-in-hand"));
            return null;
        }
        return hand;
    }

    private void createOrUpdate(CommandSender sender, CustomItemManager manager, LanguageManager lang, String[] args,
                                boolean create) throws ConfigException, IOException {
        if (args.length < 3) {
            sender.sendMessage(lang.prefixed("customitem-usage"));
            return;
        }
        if (create && manager.get(args[2]) != null) {
            sender.sendMessage(lang.prefixed("customitem-exists", "%id%", args[2].toLowerCase(Locale.ROOT)));
            return;
        }
        if (!create && manager.get(args[2]) == null) {
            sender.sendMessage(lang.prefixed("customitem-not-found", "%id%", args[2]));
            return;
        }
        final ItemStack item = sourceItem(sender, lang, args);
        if (item == null) {
            return;
        }
        final CustomItem result = create ? manager.create(args[2], item) : manager.update(args[2], item);
        sender.sendMessage(lang.prefixed(create ? "customitem-created" : "customitem-updated", "%id%", result.getId()));
    }

    private void delete(CommandSender sender, CustomItemManager manager, LanguageManager lang, String[] args) throws IOException {
        if (args.length < 3 || !manager.delete(args[2])) {
            sender.sendMessage(lang.prefixed("customitem-not-found", "%id%", args.length > 2 ? args[2] : ""));
            return;
        }
        sender.sendMessage(lang.prefixed("customitem-deleted", "%id%", args[2].toLowerCase(Locale.ROOT)));
    }

    private void give(CommandSender sender, CustomItemManager manager, LanguageManager lang, String[] args) {
        final CustomItem item = find(sender, manager, lang, args, 2);
        if (item == null) {
            return;
        }
        Player target = null;
        String amountText = null;
        if (args.length > 3) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null && args.length == 4 && sender instanceof Player && isNumber(args[3])) {
                target = (Player) sender;
                amountText = args[3];
            } else if (args.length > 4) {
                amountText = args[4];
            }
            if (target == null) {
                sender.sendMessage(lang.prefixed("customitem-player-not-found", "%player%", args[3]));
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(lang.prefixed("player-only"));
            return;
        }
        int amount = 1;
        if (amountText != null) {
            if (!isNumber(amountText) || Integer.parseInt(amountText) < 1 || Integer.parseInt(amountText) > 2304) {
                sender.sendMessage(lang.prefixed("customitem-invalid", "%error%", "amount 1-2304"));
                return;
            }
            amount = Integer.parseInt(amountText);
        }
        final int dropped = manager.give(item, target, amount);
        sender.sendMessage(lang.prefixed("customitem-given", "%amount%", String.valueOf(amount), "%id%", item.getId(),
                "%player%", target.getName()) + (dropped > 0 ? " \u00a7c(" + dropped + " dropped: inventory full)" : ""));
    }

    private void skill(CommandSender sender, CustomItemManager manager, LanguageManager lang, String[] args)
            throws ConfigException, IOException {
        if (args.length < 5) {
            sender.sendMessage(lang.prefixed("customitem-skill-usage"));
            return;
        }
        // args: customitem skill <op> <item> <skill> ...
        final String op = args[2].toLowerCase(Locale.ROOT);
        final CustomItem item = find(sender, manager, lang, args, 3);
        if (item == null) {
            return;
        }
        final String skill = args[4];
        switch (op) {
            case "add": {
                if (args.length < 7) {
                    sender.sendMessage(lang.prefixed("customitem-skill-usage"));
                    return;
                }
                if (TriggerType.parse(args[5]) == null) {
                    sender.sendMessage(lang.prefixed("customitem-invalid", "%error%", "unknown trigger '" + args[5] + "'"));
                    return;
                }
                final String value = args.length > 7 ? String.join(" ", Arrays.copyOfRange(args, 7, args.length)) : "true";
                manager.addSkill(item.getId(), skill, args[5], args[6], value);
                sender.sendMessage(lang.prefixed("customitem-skill-added", "%skill%", skill.toLowerCase(Locale.ROOT), "%id%", item.getId()));
                break;
            }
            case "remove":
                manager.removeSkill(item.getId(), skill);
                sender.sendMessage(lang.prefixed("customitem-skill-removed", "%skill%", skill.toLowerCase(Locale.ROOT), "%id%", item.getId()));
                break;
            case "action":
            case "condition": {
                if (args.length < 6) {
                    sender.sendMessage(lang.prefixed("customitem-skill-usage"));
                    return;
                }
                final String value = args.length > 6 ? String.join(" ", Arrays.copyOfRange(args, 6, args.length)) : "true";
                manager.addEntry(item.getId(), skill, op + "s", args[5], value);
                sender.sendMessage(lang.prefixed("customitem-entry-added", "%kind%", op, "%name%", args[5].toLowerCase(Locale.ROOT), "%skill%", skill.toLowerCase(Locale.ROOT)));
                break;
            }
            case "delaction":
            case "delcond": {
                final int number;
                try {
                    number = args.length > 5 ? Integer.parseInt(args[5]) : -1;
                } catch (NumberFormatException e) {
                    sender.sendMessage(lang.prefixed("customitem-invalid", "%error%", "'" + args[5] + "' is not a number"));
                    return;
                }
                manager.removeEntry(item.getId(), skill, op.equals("delaction") ? "actions" : "conditions", number);
                sender.sendMessage(lang.prefixed("customitem-entry-removed", "%n%", String.valueOf(number), "%skill%", skill.toLowerCase(Locale.ROOT)));
                break;
            }
            case "set": {
                if (args.length < 7) {
                    sender.sendMessage(lang.prefixed("customitem-skill-usage"));
                    return;
                }
                final String value = String.join(" ", Arrays.copyOfRange(args, 6, args.length));
                manager.setSkillOption(item.getId(), skill, args[5], value);
                sender.sendMessage(lang.prefixed("customitem-skill-set", "%option%", args[5].toLowerCase(Locale.ROOT), "%skill%", skill.toLowerCase(Locale.ROOT), "%value%", value));
                break;
            }
            default:
                sender.sendMessage(lang.prefixed("customitem-skill-usage"));
                break;
        }
    }

    private void errors(CommandSender sender, CustomItemManager manager, LanguageManager lang) {
        final List<String> errors = manager.getErrors();
        if (errors.isEmpty()) {
            sender.sendMessage(lang.prefixed("customitem-errors-none"));
            return;
        }
        sender.sendMessage(lang.prefixed("customitem-errors-header", "%count%", String.valueOf(errors.size())));
        for (int i = 0; i < Math.min(10, errors.size()); i++) {
            sender.sendMessage("\u00a7c- \u00a77" + errors.get(i));
        }
    }

    private static boolean isNumber(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ tab completion

    public List<String> tab(String[] args) {
        final CustomItemManager manager = manager();
        if (args.length == 2) {
            return filter(SUBCOMMANDS, args[1]);
        }
        final String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("skill")) {
            return tabSkill(manager, args);
        }
        if (args.length == 3 && WITH_ID.contains(sub)) {
            return filter(ids(manager), args[2]);
        }
        if (args.length == 4 && (sub.equals("create") || sub.equals("update"))) {
            return filter(Arrays.asList("from"), args[3]);
        }
        if (args.length == 5 && (sub.equals("create") || sub.equals("update")) && args[3].equalsIgnoreCase("from")) {
            final List<String> names = new ArrayList<>();
            for (SerializedItem item : ItemEditor.INSTANCE.getItemStorage().getSortedItems()) {
                names.add(item.getName());
            }
            return filter(names, args[4]);
        }
        if (args.length == 4 && sub.equals("give")) {
            final List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[3]);
        }
        return new ArrayList<>();
    }

    private List<String> tabSkill(CustomItemManager manager, String[] args) {
        if (args.length == 3) {
            return filter(SKILL_SUB, args[2]);
        }
        if (args.length == 4) {
            return filter(ids(manager), args[3]);
        }
        final String op = args[2].toLowerCase(Locale.ROOT);
        final CustomItem item = manager.get(args[3]);
        if (args.length == 5 && item != null && !op.equals("add")) {
            return filter(item.getSkillIds(), args[4]);
        }
        if (args.length == 6) {
            if (op.equals("add")) {
                final List<String> triggers = new ArrayList<>();
                for (TriggerType t : TriggerType.values()) {
                    triggers.add(t.name().toLowerCase(Locale.ROOT));
                }
                return filter(triggers, args[5]);
            }
            if (op.equals("action")) {
                return filter(new ArrayList<>(engine().getActionNames()), args[5]);
            }
            if (op.equals("condition")) {
                return filter(new ArrayList<>(engine().getConditionNames()), args[5]);
            }
            if (op.equals("set")) {
                return filter(CustomItemManager.skillOptions(), args[5]);
            }
        }
        if (args.length == 7 && op.equals("add")) {
            return filter(new ArrayList<>(engine().getActionNames()), args[6]);
        }
        return new ArrayList<>();
    }

    private static List<String> ids(CustomItemManager manager) {
        final List<String> ids = new ArrayList<>();
        for (CustomItem item : manager.all()) {
            ids.add(item.getId());
        }
        return ids;
    }

    private static List<String> filter(List<String> options, String prefix) {
        final String lower = prefix.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
