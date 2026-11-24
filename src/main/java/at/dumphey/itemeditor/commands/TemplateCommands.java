package at.dumphey.itemeditor.commands;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.template.TemplatePickerUiScreen;
import at.dumphey.itemeditor.styler.LanguageManager;
import at.dumphey.itemeditor.styler.StyleService;
import at.dumphey.itemeditor.styler.model.Template;
import com.google.common.collect.Multimap;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** /ie template ... and /ie language ... */
public class TemplateCommands {

    private static final List<String> TEMPLATE_SUBCOMMANDS = Arrays.asList("list", "apply", "reset", "inspect", "reload");

    private StyleService styler() {
        return ItemEditor.INSTANCE.getStyleService();
    }

    // ------------------------------------------------------------------ /ie template

    public void handleTemplate(CommandSender sender, String[] args) {
        final LanguageManager lang = styler().getLang();
        if (args.length == 1) {
            if (sender instanceof Player) {
                openPicker((Player) sender);
            } else {
                sendList(sender);
            }
            return;
        }

        switch (args[1].toLowerCase()) {
            case "list":
                sendList(sender);
                break;
            case "apply":
                apply(sender, args);
                break;
            case "reset":
                reset(sender);
                break;
            case "inspect":
                inspect(sender);
                break;
            case "reload":
                reload(sender);
                break;
            default:
                sender.sendMessage(lang.prefixed("template-usage"));
                break;
        }
    }

    private void openPicker(Player player) {
        final LanguageManager lang = styler().getLang();
        if (heldItem(player) == null) {
            return;
        }
        ItemEditor.INSTANCE.getUiManager().open(player, new TemplatePickerUiScreen(player, lang.msg("gui-hint-hand"),
                template -> {
                    if (applyToHand(player, template)) {
                        ItemEditor.INSTANCE.getUiManager().close(player);
                    }
                },
                () -> {
                    if (resetHand(player)) {
                        ItemEditor.INSTANCE.getUiManager().close(player);
                    }
                },
                () -> ItemEditor.INSTANCE.getUiManager().close(player)));
    }

    private void sendList(CommandSender sender) {
        final StyleService styler = styler();
        sender.sendMessage(styler.getLang().prefixed("template-list", "%count%",
                String.valueOf(styler.getTemplates().count()), "%list%", String.join(", ", styler.getTemplates().names())));
    }

    private void apply(CommandSender sender, String[] args) {
        final StyleService styler = styler();
        final LanguageManager lang = styler.getLang();
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.prefixed("player-only"));
            return;
        }
        final Player player = (Player) sender;

        final String name = args.length > 2 ? args[2].toLowerCase() : styler.getConfig().getDefaultTemplate();
        final Template template = styler.getTemplates().get(name);
        if (template == null) {
            player.sendMessage(lang.prefixed("template-not-found", "%template%", name, "%list%",
                    String.join(", ", styler.getTemplates().names())));
            return;
        }
        applyToHand(player, template);
    }

    private void reset(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(styler().getLang().prefixed("player-only"));
            return;
        }
        resetHand((Player) sender);
    }

    private boolean applyToHand(Player player, Template template) {
        final LanguageManager lang = styler().getLang();
        final ItemStack hand = heldItem(player);
        if (hand == null) {
            return false;
        }
        if (!styler().apply(hand, template)) {
            player.sendMessage(lang.prefixed("template-apply-failed"));
            return false;
        }
        player.getInventory().setItemInMainHand(hand);
        player.sendMessage(lang.prefixed("template-applied", "%template%", template.getName()));
        return true;
    }

    private boolean resetHand(Player player) {
        final LanguageManager lang = styler().getLang();
        final ItemStack hand = heldItem(player);
        if (hand == null) {
            return false;
        }
        if (!styler().reset(hand)) {
            player.sendMessage(lang.prefixed("template-reset-none"));
            return false;
        }
        player.getInventory().setItemInMainHand(hand);
        player.sendMessage(lang.prefixed("template-reset-success"));
        return true;
    }

    /** The item in the main hand, or null (after telling the player) if the hand is empty. */
    private ItemStack heldItem(Player player) {
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(styler().getLang().prefixed("no-item-in-hand"));
            return null;
        }
        return hand;
    }

    private void inspect(CommandSender sender) {
        final StyleService styler = styler();
        final LanguageManager lang = styler.getLang();
        if (!(sender instanceof Player)) {
            sender.sendMessage(lang.prefixed("player-only"));
            return;
        }
        final Player player = (Player) sender;
        final ItemStack item = heldItem(player);
        if (item == null) {
            return;
        }

        final ItemMeta meta = item.getItemMeta();
        player.sendMessage(lang.msg("inspect-header"));
        player.sendMessage(lang.msg("inspect-type", "%type%", item.getType().name()));
        player.sendMessage(lang.msg("inspect-name", "%name%",
                meta != null && meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name()));

        final Map<Enchantment, Integer> enchants = item.getEnchantments();
        player.sendMessage(lang.msg("inspect-enchants", "%count%", String.valueOf(enchants.size())));
        if (enchants.isEmpty()) {
            player.sendMessage(lang.msg("inspect-no-enchants"));
        }
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            player.sendMessage(lang.msg("inspect-enchant-line", "%key%", e.getKey().getKey().toString(),
                    "%level%", String.valueOf(e.getValue())));
        }

        final Multimap<Attribute, AttributeModifier> attrs = meta == null ? null : meta.getAttributeModifiers();
        player.sendMessage(lang.msg("inspect-attrs", "%count%", String.valueOf(attrs == null ? 0 : attrs.size())));
        if (attrs == null || attrs.isEmpty()) {
            player.sendMessage(lang.msg("inspect-no-attrs"));
        } else {
            for (Map.Entry<Attribute, AttributeModifier> e : attrs.entries()) {
                player.sendMessage(lang.msg("inspect-attr-line", "%attr%", e.getKey().getKey().getKey(),
                        "%value%", String.format(Locale.ROOT, "%.4f", e.getValue().getAmount()),
                        "%op%", e.getValue().getOperation().name()));
            }
        }

        final String applied = styler.getMetadata().getAppliedTemplate(meta);
        player.sendMessage(applied != null ? lang.msg("inspect-template", "%template%", applied)
                : lang.msg("inspect-no-template"));
        player.sendMessage(lang.msg("inspect-footer"));
    }

    private void reload(CommandSender sender) {
        final StyleService styler = styler();
        styler.reload();
        sender.sendMessage(styler.getLang().prefixed("template-reloaded",
                "%templates%", String.valueOf(styler.getTemplates().count()),
                "%enchants%", String.valueOf(styler.getEnchants().vanillaCount() + styler.getEnchants().customCount()),
                "%attributes%", String.valueOf(styler.getAttributes().count()),
                "%lang%", styler.getLang().getActiveLang()));
    }

    // ------------------------------------------------------------------ /ie language

    public void handleLanguage(CommandSender sender, String[] args) {
        final LanguageManager lang = styler().getLang();
        final String list = String.join(", ", lang.availableLanguages());
        if (args.length < 2) {
            sender.sendMessage(lang.prefixed("language-current", "%lang%", lang.getActiveLang(), "%list%", list));
            return;
        }
        final String wanted = args[1].toLowerCase();
        if (lang.setLanguage(wanted)) {
            sender.sendMessage(lang.prefixed("language-changed", "%lang%", wanted));
        } else {
            sender.sendMessage(lang.prefixed("language-not-found", "%lang%", wanted, "%list%", list));
        }
    }

    // ------------------------------------------------------------------ tab completion

    public List<String> tabTemplate(String[] args) {
        if (args.length == 2) {
            return filter(TEMPLATE_SUBCOMMANDS, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("apply")) {
            return filter(styler().getTemplates().names(), args[2]);
        }
        return new ArrayList<>();
    }

    public List<String> tabLanguage(String[] args) {
        if (args.length == 2) {
            return filter(styler().getLang().availableLanguages(), args[1]);
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
