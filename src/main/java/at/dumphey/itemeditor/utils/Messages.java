package at.dumphey.itemeditor.utils;

import org.bukkit.command.CommandSender;

public class Messages {

    /** Start of every chat message. */
    public static final String PREFIX = "§6§lUltimate§e§lItemsEditor§8";
    public static final String CHAT_PREFIX = PREFIX + " - §f";
    /** Short prefix of the inventory titles (a long one does not fit in the title bar). */
    public static final String TITLE_PREFIX = "§6§lUIE §8»";

    public static void send(CommandSender target, String message) {
        target.sendMessage(CHAT_PREFIX + message);
    }
}
