package at.dumphey.itemeditor.engine;

import org.bukkit.ChatColor;

import java.util.Locale;

enum EnchantRarity {
    COMMON(ChatColor.WHITE, 60, 45),
    UNCOMMON(ChatColor.GREEN, 30, 27),
    RARE(ChatColor.AQUA, 12, 15),
    EPIC(ChatColor.LIGHT_PURPLE, 4, 7),
    LEGENDARY(ChatColor.GOLD, 1, 2);

    final ChatColor color;
    final int tableWeight;
    final int villagerWeight;

    EnchantRarity(ChatColor color, int tableWeight, int villagerWeight) {
        this.color = color;
        this.tableWeight = tableWeight;
        this.villagerWeight = villagerWeight;
    }

    static EnchantRarity parse(Object value, ParseContext pc) throws ConfigException {
        try {
            return valueOf(String.valueOf(value).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw pc.error("expected common, uncommon, rare, epic or legendary");
        }
    }

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
