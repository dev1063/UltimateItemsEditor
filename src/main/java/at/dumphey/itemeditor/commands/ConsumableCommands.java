package at.dumphey.itemeditor.commands;

import at.dumphey.itemeditor.engine.ConfigException;
import at.dumphey.itemeditor.engine.Consumable;
import at.dumphey.itemeditor.engine.ConsumableManager;
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

/** /ie consumable ... */
public class ConsumableCommands {

    private static final List<String> SUBCOMMANDS = Arrays.asList("create", "update", "addcmd", "delcmd", "addaction", "delaction", "set", "give",
            "info", "list", "delete", "reload", "errors");
    private static final List<String> WITH_ID = Arrays.asList("update", "addcmd", "delcmd", "addaction", "delaction", "set", "give", "info", "delete");

    private ConsumableManager manager() {
        return ItemEditor.INSTANCE.getTriggerEngine().consumables();
    }

    private LanguageManager lang() {
        return ItemEditor.INSTANCE.getStyleService().getLang();
    }

    public void handle(CommandSender sender, String[] args) {
        final LanguageManager lang = lang();
        final ConsumableManager manager = manager();
        if (args.length < 2) {
            sender.sendMessage(lang.prefixed("consumable-usage"));
            return;
        }
        final String sub = args[1].toLowerCase(Locale.ROOT);
        if (!manager.isEnabled() && !sub.equals("reload")) {
            sender.sendMessage(lang.prefixed("consumable-disabled"));
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
                case "addcmd":
                    addCommand(sender, manager, lang, args);
                    break;
                case "delcmd":
                    removeCommand(sender, manager, lang, args);
                    break;
                case "addaction":
                    addAction(sender, manager, lang, args);
                    break;
                case "delaction":
                    removeAction(sender, manager, lang, args);
                    break;
                case "set":
                    set(sender, manager, lang, args);
                    break;
                case "give":
                    give(sender, manager, lang, args);
                    break;
                case "delete":
                    delete(sender, manager, lang, args);
                    break;
                case "reload":
                    ItemEditor.INSTANCE.getTriggerEngine().plugin().reloadConfig();
                    ItemEditor.INSTANCE.getTriggerEngine().load();
                    sender.sendMessage(lang.prefixed("consumable-reloaded", "%count%", String.valueOf(manager.all().size()),
                            "%errors%", String.valueOf(manager.getErrors().size())));
                    if (!manager.getErrors().isEmpty()) {
                        errors(sender, manager, lang);
                    }
                    break;
                case "errors":
                    errors(sender, manager, lang);
                    break;
                default:
                    sender.sendMessage(lang.prefixed("consumable-usage"));
                    break;
            }
        } catch (ConfigException e) {
            sender.sendMessage(lang.prefixed("consumable-invalid", "%error%", e.getMessage()));
        } catch (IOException e) {
            ItemEditor.INSTANCE.getTriggerEngine().plugin().getLogger().log(Level.SEVERE, "Could not save a consumable", e);
            sender.sendMessage(lang.prefixed("consumable-save-failed"));
        }
    }

    private void list(CommandSender sender, ConsumableManager manager, LanguageManager lang) {
        if (manager.all().isEmpty()) {
            sender.sendMessage(lang.prefixed("consumable-none"));
            return;
        }
        final List<String> ids = new ArrayList<>();
        for (Consumable c : manager.all()) {
            ids.add((c.isUsable() ? "" : "\u00a78") + c.getId() + "\u00a77");
        }
        sender.sendMessage(lang.prefixed("consumable-list", "%count%", String.valueOf(ids.size()), "%list%", String.join(", ", ids)));
    }

    private Consumable find(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args, int index) {
        final Consumable c = args.length > index ? manager.get(args[index]) : null;
        if (c == null) {
            sender.sendMessage(lang.prefixed("consumable-not-found", "%id%", args.length > index ? args[index] : ""));
        }
        return c;
    }

    private void info(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args) {
        final Consumable c = find(sender, manager, lang, args, 2);
        if (c == null) {
            return;
        }
        sender.sendMessage(lang.msg("consumable-info-header", "%id%", c.getId()));
        sender.sendMessage(lang.msg("consumable-info-item", "%material%", c.getTemplate().getType().name(),
                "%click%", c.getClickText(), "%amount%", String.valueOf(c.getAmount()),
                "%cooldown%", c.getCooldownMs() == 0 ? "-" : (c.getCooldownMs() / 1000.0) + "s"));
        for (int i = 0; i < c.getCommands().size(); i++) {
            sender.sendMessage(lang.msg("consumable-info-command", "%n%", String.valueOf(i + 1), "%command%", c.getCommands().get(i)));
        }
        final List<String> actions = c.getActionDescriptions();
        for (int i = 0; i < actions.size(); i++) {
            sender.sendMessage(lang.msg("consumable-info-action", "%n%", String.valueOf(i + 1), "%action%", actions.get(i)));
        }
        if (!c.isUsable()) {
            sender.sendMessage(lang.msg("consumable-info-empty"));
        }
        sender.sendMessage(lang.msg("consumable-info-extra", "%conditions%", c.hasConditions() ? "yes" : "no",
                "%actions%", String.valueOf(c.getExtraActions())));
    }

    /** The item to store: a saved item ("from <name>") or the item in the hand. Null (after telling the sender) if none. */
    private ItemStack sourceItem(CommandSender sender, LanguageManager lang, String[] args) {
        if (args.length > 3 && args[3].equalsIgnoreCase("from")) {
            final SerializedItem saved = args.length > 4 ? ItemEditor.INSTANCE.getItemStorage().getItem(args[4]) : null;
            if (saved == null) {
                sender.sendMessage(lang.prefixed("consumable-saved-not-found", "%name%", args.length > 4 ? args[4] : ""));
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

    private void createOrUpdate(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args,
                                boolean create) throws ConfigException, IOException {
        if (args.length < 3) {
            sender.sendMessage(lang.prefixed("consumable-usage"));
            return;
        }
        if (create && manager.get(args[2]) != null) {
            sender.sendMessage(lang.prefixed("consumable-exists", "%id%", args[2].toLowerCase(Locale.ROOT)));
            return;
        }
        if (!create && manager.get(args[2]) == null) {
            sender.sendMessage(lang.prefixed("consumable-not-found", "%id%", args[2]));
            return;
        }
        final ItemStack item = sourceItem(sender, lang, args);
        if (item == null) {
            return;
        }
        final Consumable c = create ? manager.create(args[2], item) : manager.update(args[2], item);
        sender.sendMessage(lang.prefixed(create ? "consumable-created" : "consumable-updated", "%id%", c.getId()));
    }

    private void addCommand(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args)
            throws ConfigException, IOException {
        final Consumable c = find(sender, manager, lang, args, 2);
        if (c == null) {
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(lang.prefixed("consumable-usage"));
            return;
        }
        final String command = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
        final int number = manager.addCommand(c.getId(), command);
        sender.sendMessage(lang.prefixed("consumable-cmd-added", "%n%", String.valueOf(number), "%id%", c.getId(),
                "%command%", command));
    }

    private void removeCommand(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args)
            throws ConfigException, IOException {
        final Consumable c = find(sender, manager, lang, args, 2);
        if (c == null) {
            return;
        }
        final int number;
        try {
            number = args.length > 3 ? Integer.parseInt(args[3]) : -1;
        } catch (NumberFormatException e) {
            sender.sendMessage(lang.prefixed("consumable-cmd-bad-index", "%n%", args[3], "%id%", c.getId()));
            return;
        }
        if (manager.removeCommand(c.getId(), number)) {
            sender.sendMessage(lang.prefixed("consumable-cmd-removed", "%n%", String.valueOf(number), "%id%", c.getId()));
        } else {
            sender.sendMessage(lang.prefixed("consumable-cmd-bad-index", "%n%", String.valueOf(number), "%id%", c.getId()));
        }
    }

    private void addAction(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args)
            throws ConfigException, IOException {
        if (args.length < 5) {
            sender.sendMessage(lang.prefixed("consumable-usage"));
            return;
        }
        final Consumable consumable = find(sender, manager, lang, args, 2);
        if (consumable == null) {
            return;
        }
        final String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        final int number = manager.addAction(consumable.getId(), args[3], value);
        sender.sendMessage(lang.prefixed("consumable-action-added", "%n%", String.valueOf(number), "%id%", consumable.getId()));
    }

    private void removeAction(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args)
            throws ConfigException, IOException {
        if (args.length < 4) {
            sender.sendMessage(lang.prefixed("consumable-usage"));
            return;
        }
        final Consumable consumable = find(sender, manager, lang, args, 2);
        if (consumable == null) {
            return;
        }
        final boolean removed = isNumber(args[3]) && manager.removeAction(consumable.getId(), Integer.parseInt(args[3]));
        sender.sendMessage(lang.prefixed(removed ? "consumable-action-removed" : "consumable-action-bad-index",
                "%n%", args[3], "%id%", consumable.getId()));
    }

    private void set(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args)
            throws ConfigException, IOException {
        final Consumable c = find(sender, manager, lang, args, 2);
        if (c == null) {
            return;
        }
        if (args.length < 5) {
            sender.sendMessage(lang.prefixed("consumable-usage"));
            return;
        }
        final String value = String.join(" ", Arrays.copyOfRange(args, 4, args.length));
        manager.setOption(c.getId(), args[3], value);
        sender.sendMessage(lang.prefixed("consumable-set", "%option%", args[3].toLowerCase(Locale.ROOT), "%id%", c.getId(), "%value%", value));
    }

    private void give(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args) {
        final Consumable c = find(sender, manager, lang, args, 2);
        if (c == null) {
            return;
        }
        Player target = null;
        int amount = 1;
        String amountText = null;
        if (args.length > 3) {
            target = Bukkit.getPlayerExact(args[3]);
            if (target == null && args.length == 4 && sender instanceof Player && isNumber(args[3])) {
                target = (Player) sender; // "/ie consumable give <id> 5"
                amountText = args[3];
            } else if (args.length > 4) {
                amountText = args[4];
            }
            if (target == null) {
                sender.sendMessage(lang.prefixed("consumable-player-not-found", "%player%", args[3]));
                return;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(lang.prefixed("player-only"));
            return;
        }
        if (amountText != null) {
            if (!isNumber(amountText) || Integer.parseInt(amountText) < 1 || Integer.parseInt(amountText) > 2304) {
                sender.sendMessage(lang.prefixed("consumable-invalid", "%error%", "amount 1-2304"));
                return;
            }
            amount = Integer.parseInt(amountText);
        }
        final int dropped = manager.give(c, target, amount);
        sender.sendMessage(lang.prefixed("consumable-given", "%amount%", String.valueOf(amount), "%id%", c.getId(),
                "%player%", target.getName()) + (dropped > 0 ? " \u00a7c(" + dropped + " dropped: inventory full)" : ""));
    }

    private void delete(CommandSender sender, ConsumableManager manager, LanguageManager lang, String[] args) throws IOException {
        if (args.length < 3 || !manager.delete(args[2])) {
            sender.sendMessage(lang.prefixed("consumable-not-found", "%id%", args.length > 2 ? args[2] : ""));
            return;
        }
        sender.sendMessage(lang.prefixed("consumable-deleted", "%id%", args[2].toLowerCase(Locale.ROOT)));
    }

    private void errors(CommandSender sender, ConsumableManager manager, LanguageManager lang) {
        final List<String> errors = manager.getErrors();
        if (errors.isEmpty()) {
            sender.sendMessage(lang.prefixed("consumable-errors-none"));
            return;
        }
        sender.sendMessage(lang.prefixed("consumable-errors-header", "%count%", String.valueOf(errors.size())));
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
        final ConsumableManager manager = manager();
        if (args.length == 2) {
            return filter(SUBCOMMANDS, args[1]);
        }
        final String sub = args[1].toLowerCase(Locale.ROOT);
        if (args.length == 3 && WITH_ID.contains(sub)) {
            final List<String> ids = new ArrayList<>();
            for (Consumable c : manager.all()) {
                ids.add(c.getId());
            }
            return filter(ids, args[2]);
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
        if (args.length == 4 && sub.equals("addaction")) {
            return filter(new ArrayList<>(ItemEditor.INSTANCE.getTriggerEngine().getActionNames()), args[3]);
        }
        if (args.length == 4 && sub.equals("set")) {
            return filter(ConsumableManager.options(), args[3]);
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
