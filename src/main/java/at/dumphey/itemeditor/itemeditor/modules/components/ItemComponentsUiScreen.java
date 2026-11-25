package at.dumphey.itemeditor.itemeditor.modules.components;

import at.dumphey.itemeditor.UltimateItemsEditor;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.home.HomeUiScreen;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.prompts.ChatPrompt;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.inventory.meta.components.ToolComponent;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Native 1.21.4 item metadata, exposed only when its Bukkit API is available. */
public final class ItemComponentsUiScreen extends UiScreen {
    public ItemComponentsUiScreen(Player player) {
        super(player, 36, "Componentes del ítem");
    }

    @Override
    protected void onUpdate() {
        final ItemStack stack = getEditedItem();
        final ItemMeta meta = stack == null ? null : stack.getItemMeta();
        if (meta != null) {
            button(10, Material.APPLE, "Comida", "Nutrición y saturación", () -> promptValue(
                    "Escribe nutrición y saturación, separadas por un espacio. Ejemplo: 6 2.4",
                    value -> {
                        final String[] parts = value.trim().split("\\s+");
                        if (parts.length != 2) throw new IllegalArgumentException();
                        final int nutrition = Integer.parseInt(parts[0]);
                        final float saturation = Float.parseFloat(parts[1]);
                        if (nutrition < 0 || nutrition > 20 || !Float.isFinite(saturation)
                                || saturation < 0 || saturation > 20) throw new IllegalArgumentException();
                        editMeta(next -> {
                            final FoodComponent food = next.getFood();
                            food.setNutrition(nutrition);
                            food.setSaturation(saturation);
                            next.setFood(food);
                            if (!next.hasConsumable()) next.setConsumable(next.getConsumable());
                        });
                    }));
            button(12, Material.IRON_PICKAXE, "Herramienta", "Velocidad base y desgaste por bloque", () -> promptValue(
                    "Escribe velocidad base y desgaste por bloque. Ejemplo: 4.5 1",
                    value -> {
                        final String[] parts = value.trim().split("\\s+");
                        if (parts.length != 2) throw new IllegalArgumentException();
                        final float speed = Float.parseFloat(parts[0]);
                        final int damage = Integer.parseInt(parts[1]);
                        if (!Float.isFinite(speed) || speed <= 0 || speed > 100 || damage < 0 || damage > 32)
                            throw new IllegalArgumentException();
                        editMeta(next -> {
                            final ToolComponent tool = next.getTool();
                            tool.setDefaultMiningSpeed(speed);
                            final List<ToolComponent.ToolRule> rules = tool.getRules();
                            for (ToolComponent.ToolRule rule : rules) {
                                if (rule.getSpeed() != null) rule.setSpeed(speed);
                            }
                            tool.setRules(rules);
                            tool.setDamagePerBlock(damage);
                            next.setTool(tool);
                        });
                    }));
            button(14, Material.NAME_TAG, "Rareza vanilla", meta.hasRarity() ? meta.getRarity().name() : "Predeterminada",
                    () -> promptValue("Escribe common, uncommon, rare, epic o none.", value -> {
                        final String key = value.trim().toUpperCase(Locale.ROOT);
                        final ItemRarity rarity = "NONE".equals(key) ? null : ItemRarity.valueOf(key);
                        editMeta(next -> next.setRarity(rarity));
                    }));
            button(16, Material.PAPER, "CustomModelData", "Modelo de un paquete de recursos",
                    () -> promptValue("Escribe el ID numérico del modelo, o none.", value -> {
                        final String text = value.trim();
                        final Integer model = "none".equalsIgnoreCase(text) ? null : Integer.valueOf(text);
                        editMeta(next -> next.setCustomModelData(model));
                    }));
            if (meta instanceof Damageable && stack.getType().getMaxDurability() > 0) {
                button(20, Material.ANVIL, "Durabilidad máxima", "Componente de desgaste",
                        () -> promptValue("Escribe la durabilidad máxima (1–1000000) o none.", value -> {
                            final String text = value.trim();
                            final Integer maximum = "none".equalsIgnoreCase(text) ? null : Integer.valueOf(text);
                            if (maximum != null && (maximum < 1 || maximum > 1_000_000)) {
                                throw new IllegalArgumentException();
                            }
                            editMeta(next -> {
                                final Damageable damageable = (Damageable) next;
                                final int limit = maximum == null ? stack.getType().getMaxDurability() : maximum;
                                damageable.setMaxDamage(maximum);
                                damageable.setDamage(Math.min(damageable.getDamage(), limit - 1));
                            });
                        }));
            }
            final ArmorSets sets = UltimateItemsEditor.getInstance().armorSets();
            if (sets != null && !sets.ids().isEmpty() && isArmor(stack.getType())) {
                final String current = sets.id(meta);
                button(24, Material.IRON_CHESTPLATE, "Conjunto de armadura", current == null ? "Sin conjunto" : current,
                        () -> promptValue("Conjuntos " + String.join(", ", sets.ids()) + ". Escribe un ID o none.", value -> {
                            final String id = value.trim().toLowerCase(Locale.ROOT);
                            if (!id.equals("none") && !sets.ids().contains(id)) throw new IllegalArgumentException();
                            editMeta(next -> sets.setId(next, id.equals("none") ? null : id));
                        }));
            }
            button(22, Material.NETHER_STAR, "Alternar brillo", "El ítem conserva sus encantamientos", () -> {
                editMeta(next -> next.setEnchantmentGlintOverride(!next.hasEnchantmentGlintOverride()
                        || !next.getEnchantmentGlintOverride()));
                update();
            });
        }
        button(35, Material.ARROW, "Volver", "Editor del ítem", () -> open(new HomeUiScreen(player)));
        fillEmptyWithFiller();
    }

    private static boolean isArmor(Material material) {
        final String name = material.name();
        return material == Material.ELYTRA || name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private void editMeta(Consumer<ItemMeta> edit) {
        modifyItem(item -> ItemBuilder.of(item).editMeta(edit).build());
    }

    private void button(int slot, Material icon, String label, String lore, Runnable action) {
        setItem(slot, new ActionUiItem(this, player, ItemBuilder.uiItem(icon, label, lore), action));
    }

    private void promptValue(String message, Consumer<String> configure) {
        final ItemComponentsUiScreen screen = this;
        Bukkit.getScheduler().runTask(UltimateItemsEditor.getInstance(), () -> {
            if (!player.isOnline() || !screen.isInventory(player.getOpenInventory().getTopInventory())) return;
            prompt(new ChatPrompt(player, "§e" + message, true) {
                @Override protected void onCancelled() { open(screen); }

                @Override protected boolean validateInput(String value) {
                    try {
                        configure.accept(value);
                        return true;
                    } catch (IllegalArgumentException ex) {
                        player.sendMessage("§cValor no válido. Escribe otro valor o cancel.");
                        return false;
                    }
                }

                @Override protected void onFulfilled(String value) { open(screen); }
            });
        });
    }
}
