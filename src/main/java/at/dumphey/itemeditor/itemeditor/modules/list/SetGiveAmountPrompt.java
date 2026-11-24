package at.dumphey.itemeditor.itemeditor.modules.list;

import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.ui.prompts.ChatPrompt;
import at.dumphey.itemeditor.utils.Messages;
import org.bukkit.entity.Player;

public class SetGiveAmountPrompt extends ChatPrompt {

    private final ItemGiveUiScreen screen;

    public SetGiveAmountPrompt(Player player, ItemGiveUiScreen screen) {
        super(player, Messages.CHAT_PREFIX + "Enter the amount to give (§o1-" + ItemGiveUiScreen.MAX_AMOUNT + "§f):",
                true);
        this.screen = screen;
    }

    @Override
    protected void onCancelled() {
        ItemEditor.INSTANCE.getUiManager().reopen(player);
    }

    @Override
    protected void onFulfilled(String input) {
        screen.setAmount(Integer.parseInt(input.trim()));
        screen.update();
        Messages.send(player, "Amount set to §o" + screen.getAmount() + "§f.");
        ItemEditor.INSTANCE.getUiManager().reopen(player);
    }

    @Override
    protected boolean validateInput(String input) {
        try {
            final int value = Integer.parseInt(input.trim());
            if (value >= 1 && value <= ItemGiveUiScreen.MAX_AMOUNT) {
                return true;
            }
        } catch (NumberFormatException ignored) {
            // falls through to the error message
        }
        player.sendMessage("§c§oInvalid input: " + input + " (use a number from 1 to " + ItemGiveUiScreen.MAX_AMOUNT + ")");
        return false;
    }
}
