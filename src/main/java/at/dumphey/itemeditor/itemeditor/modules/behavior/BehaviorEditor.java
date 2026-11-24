package at.dumphey.itemeditor.itemeditor.modules.behavior;

import at.dumphey.itemeditor.engine.ConfigException;
import at.dumphey.itemeditor.engine.Consumable;
import at.dumphey.itemeditor.engine.ConsumableManager;
import at.dumphey.itemeditor.engine.CustomItem;
import at.dumphey.itemeditor.engine.CustomItemManager;
import at.dumphey.itemeditor.engine.Rule;
import at.dumphey.itemeditor.engine.TriggerEngine;
import at.dumphey.itemeditor.engine.TriggerType;
import at.dumphey.itemeditor.itemeditor.ItemEditor;
import at.dumphey.itemeditor.itemeditor.modules.home.HomeUiScreen;
import at.dumphey.itemeditor.itemeditor.modules.template.ConfirmUiScreen;
import at.dumphey.itemeditor.ui.design.PageBar;
import at.dumphey.itemeditor.ui.items.ActionUiItem;
import at.dumphey.itemeditor.ui.prompts.ChatPrompt;
import at.dumphey.itemeditor.ui.template.UiItem;
import at.dumphey.itemeditor.ui.template.UiScreen;
import at.dumphey.itemeditor.utils.ItemBuilder;
import at.dumphey.itemeditor.utils.Permissions;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/** Inventory editor for the same item definitions used by the commands and YAML loaders. */
public final class BehaviorEditor {
    private final Player player;
    private final TriggerEngine engine;

    public BehaviorEditor(Player player) {
        this.player = player;
        this.engine = ItemEditor.INSTANCE.getTriggerEngine();
    }

    public void open(boolean consumable) {
        if (!player.hasPermission(Permissions.WILDCARD)) {
            return;
        }
        final Menu menu = new Menu(consumable ? "Consumibles" : "Ítems personalizados",
                () -> show(new HomeUiScreen(player)));
        menu.button(53, Material.LIME_DYE, "Crear desde el ítem editado", () -> create(consumable));
        final List<String> ids = new ArrayList<>();
        if (consumable) {
            for (Consumable item : consumables().all()) {
                ids.add(item.getId());
            }
        } else {
            for (CustomItem item : customItems().all()) {
                ids.add(item.getId());
            }
        }
        Collections.sort(ids);
        for (String id : ids) {
            menu.choice(consumable ? Material.HONEY_BOTTLE : Material.NETHER_STAR, id,
                    consumable ? () -> consumable(id) : () -> customItem(id));
        }
        show(menu);
    }

    private void create(boolean consumable) {
        if (consumable ? !consumables().isEnabled() : !customItems().isEnabled()) {
            player.sendMessage("§cEsta función está desactivada en config.yml.");
            return;
        }
        final ItemStack edited = ItemEditor.INSTANCE.getItem(player);
        if (edited == null || edited.getType().isAir()) {
            player.sendMessage("§cAbre el editor con un ítem antes de crear una definición.");
            return;
        }
        final ItemStack source = edited.clone();
        input("Escribe un ID único (letras, números, _ o -).", id -> {
            if (consumable) {
                consumable(consumables().create(id.trim(), source).getId());
            } else {
                customItem(customItems().create(id.trim(), source).getId());
            }
        }, () -> open(consumable));
    }

    private void consumable(String id) {
        final Consumable item = consumables().get(id);
        if (item == null) {
            open(true);
            return;
        }
        final Runnable back = () -> consumable(id);
        final Menu menu = new Menu("Consumible: " + id, () -> open(true));
        menu.choice(Material.COMMAND_BLOCK, "Comandos", () -> entries(id, null, Entries.COMMANDS));
        menu.choice(Material.BLAZE_POWDER, "Efectos y mensajes", () -> entries(id, null, Entries.ACTIONS));
        menu.choice(Material.COMPARATOR, "Condiciones", () -> entries(id, null, Entries.CONDITIONS));
        menu.choice(Material.CLOCK, "Cooldown: " + seconds(item.getCooldownMs()),
                () -> cooldown(value -> consumables().setOption(id, "cooldown", value), back));
        menu.choice(Material.LEVER, "Clic: " + item.getClickText(), () -> {
            final Menu clicks = new Menu("Clic de activación", back);
            for (String click : Arrays.asList("right", "left", "any", "right-air", "right-block", "left-air", "left-block")) {
                clicks.choice(Material.LEVER, click, () -> change(() -> consumables().setOption(id, "click", click), back));
            }
            show(clicks);
        });
        menu.choice(Material.HONEY_BOTTLE, "Cantidad por uso: " + item.getAmount(), () ->
                input("Escribe la cantidad que se gasta en cada uso (1–64).",
                        value -> consumables().setOption(id, "amount", value), back));
        menu.button(53, Material.CHEST, "Recibir 1 consumible", () -> change(() -> {
            final Consumable current = consumables().get(id);
            if (current != null) {
                consumables().give(current, player, 1);
                player.sendMessage("§aRecibiste " + id + ".");
            }
        }, back));
        show(menu);
    }

    private void customItem(String id) {
        final CustomItem item = customItems().get(id);
        if (item == null) {
            open(false);
            return;
        }
        final Runnable back = () -> customItem(id);
        final Menu menu = new Menu("Personalizado: " + id, () -> open(false));
        for (String skill : item.getSkillIds()) {
            menu.choice(Material.ENCHANTED_BOOK, skill, () -> skill(id, skill));
        }
        menu.button(45, Material.LIME_DYE, "Crear habilidad", () ->
                input("Escribe el ID de la habilidad.", name -> triggers("Activador de la habilidad", back, trigger ->
                        chooseEntry(false, (type, value) -> change(
                                () -> customItems().addSkill(id, name.trim(), trigger.name(), type, value),
                                () -> skill(id, name.trim())), back)), back));
        menu.button(53, Material.CHEST, "Recibir 1 ítem personalizado", () -> change(() -> {
            final CustomItem current = customItems().get(id);
            if (current != null) {
                customItems().give(current, player, 1);
                player.sendMessage("§aRecibiste " + id + ".");
            }
        }, back));
        show(menu);
    }

    private void skill(String id, String skill) {
        final CustomItem item = customItems().get(id);
        if (item == null || !item.getSkillIds().contains(skill.toLowerCase(Locale.ROOT))) {
            customItem(id);
            return;
        }
        final Runnable back = () -> skill(id, skill);
        final Rule rule = item.getSkill(skill);
        final Menu menu = new Menu("Habilidad: " + skill, () -> customItem(id));
        menu.choice(Material.BLAZE_POWDER, "Comandos, efectos y mensajes", () -> entries(id, skill, Entries.ACTIONS));
        menu.choice(Material.COMPARATOR, "Condiciones", () -> entries(id, skill, Entries.CONDITIONS));
        menu.choice(Material.CLOCK, rule == null ? "Configurar cooldown" : "Cooldown: " + seconds(rule.getCooldownMs()),
                () -> cooldown(value -> customItems().setSkillOption(id, skill, "cooldown", value), back));
        menu.choice(Material.LEVER, rule == null ? "Configurar activador" : "Activador: " + rule.getTrigger().name(),
                () -> triggers("Activador de la habilidad", back, trigger -> change(
                        () -> customItems().setSkillOption(id, skill, "trigger", trigger.name()), back)));
        menu.choice(Material.ARMOR_STAND, "Ranuras de activación", () -> {
            final Menu slots = new Menu("Ranuras", back);
            for (String slot : Arrays.asList("hand", "off_hand", "hands", "head", "chest", "legs", "feet", "armor", "any")) {
                slots.choice(Material.ARMOR_STAND, slot, () -> change(
                        () -> customItems().setSkillOption(id, skill, "slots", slot), back));
            }
            show(slots);
        });
        menu.choice(Material.PAPER, "Mensaje de cooldown", () ->
                input("Escribe el aviso. Usa %cooldown% para el tiempo restante o none para quitarlo.",
                        value -> customItems().setSkillOption(id, skill, "cooldown-message", value), back));
        if (rule != null && rule.getTrigger() == TriggerType.PERIODIC) {
            menu.choice(Material.REPEATER, "Intervalo periódico", () ->
                    input("Escribe el tiempo entre activaciones (por ejemplo 5s).",
                            value -> customItems().setSkillOption(id, skill, "interval", value), back));
        }
        menu.button(45, rule == null ? Material.LIME_DYE : Material.RED_DYE,
                rule == null ? "Activar habilidad" : "Desactivar habilidad", () -> change(
                        () -> customItems().setSkillOption(id, skill, "enabled", String.valueOf(rule == null)), back));
        menu.button(53, Material.BARRIER, "Eliminar habilidad", () -> confirm("Eliminar " + skill, () -> {
            if (customItems().get(id) != item) {
                throw changed();
            }
            customItems().removeSkill(id, skill);
        }, () -> customItem(id)));
        show(menu);
    }

    private void entries(String id, String skill, Entries kind) {
        final boolean custom = skill != null;
        final CustomItem customItem = custom ? customItems().get(id) : null;
        final Consumable consumable = custom ? null : consumables().get(id);
        final Runnable parent = () -> { if (custom) skill(id, skill); else consumable(id); };
        if (custom ? customItem == null : consumable == null) {
            parent.run();
            return;
        }
        final boolean conditions = kind == Entries.CONDITIONS;
        final String list = conditions ? "conditions" : "actions";
        final List<String> values;
        switch (kind) {
            case CONDITIONS:
                values = custom ? customItem.getSkillEntries(skill, true) : consumable.getConditionDescriptions();
                break;
            case COMMANDS:
                values = consumable.getCommands();
                break;
            default:
                values = custom ? customItem.getSkillEntries(skill, false) : consumable.getActionDescriptions();
        }
        final Runnable back = () -> entries(id, skill, kind);
        final Menu menu = new Menu(kind.title, parent);
        for (int i = 0; i < values.size(); i++) {
            final int number = i + 1;
            final String description = values.get(i).replace('\n', ' ').replace('\r', ' ');
            final String label = description.length() > 58 ? description.substring(0, 55) + "..." : description;
            menu.choice(kind.icon, "#" + number + " " + label, () -> confirm("Eliminar entrada #" + number, () -> {
                if (custom ? customItems().get(id) != customItem : consumables().get(id) != consumable) {
                    throw changed();
                }
                if (custom) {
                    customItems().removeEntry(id, skill, list, number, true);
                    if (kind == Entries.ACTIONS && values.size() == 1) {
                        player.sendMessage("§eLa habilidad quedó desactivada. Añade una acción y vuelve a activarla.");
                    }
                } else if (kind == Entries.COMMANDS) {
                    consumables().removeCommand(id, number);
                } else if (conditions) {
                    consumables().removeCondition(id, number);
                } else {
                    consumables().removeAction(id, number);
                }
            }, back), "Clic para eliminar");
        }
        menu.button(53, Material.LIME_DYE, "Añadir", () -> {
            if (kind == Entries.COMMANDS) {
                input("Escribe el comando completo como texto, sin la barra inicial (/).",
                        value -> consumables().addCommand(id, value), back);
                return;
            }
            chooseEntry(conditions, (type, value) -> change(() -> {
                if (custom) {
                    customItems().addEntry(id, skill, list, type, value);
                } else if (conditions) {
                    consumables().addCondition(id, type, value);
                } else {
                    consumables().addEffect(id, type, value);
                }
            }, back), back);
        });
        show(menu);
    }

    private void chooseEntry(boolean condition, BiConsumer<String, Object> add, Runnable back) {
        final Menu menu = new Menu(condition ? "Añadir condición" : "Añadir acción", back);
        if (condition) {
            branch(menu, Material.LEATHER_BOOTS, "Agachado", "sneaking", add);
            branch(menu, Material.FEATHER, "Esprintando", "sprinting", add);
            branch(menu, Material.ELYTRA, "Volando", "flying", add);
            branch(menu, Material.PLAYER_HEAD, "Objetivo jugador", "target-player", add);
            text(menu, Material.TRIPWIRE_HOOK, "Permiso", "permission", "Escribe el nodo de permiso.", add);
            text(menu, Material.GRASS_BLOCK, "Mundo", "world", "Escribe el nombre del mundo.", add);
            text(menu, Material.PAPER, "Probabilidad", "chance", "Escribe un porcentaje de 0 a 100.", add);
            menu.choice(Material.DIAMOND_SWORD, "Modo de juego", () -> {
                final Menu modes = new Menu("Modo de juego", () -> show(menu));
                for (GameMode mode : GameMode.values()) {
                    modes.choice(Material.DIAMOND_SWORD, mode.name(), () -> add.accept("gamemode", mode.name()));
                }
                show(modes);
            });
            menu.choice(Material.LEVER, "Tipo de clic", () -> {
                final Menu clicks = new Menu("Tipo de clic", () -> show(menu));
                for (String click : Arrays.asList("right", "left", "right-air", "right-block", "left-air", "left-block")) {
                    clicks.choice(Material.LEVER, click, () -> add.accept("click", click));
                }
                show(clicks);
            });
        } else {
            text(menu, Material.COMMAND_BLOCK, "Comando de consola", "command", "Escribe el comando sin la barra inicial (/).", add);
            text(menu, Material.PAPER, "Mensaje de chat", "message", "Escribe el mensaje. Puedes usar &a y %player%.", add);
            text(menu, Material.NAME_TAG, "Título en pantalla", "title", "Escribe el título.", add);
            text(menu, Material.OAK_SIGN, "Mensaje sobre la barra", "actionbar", "Escribe el mensaje de la barra.", add);
            menu.choice(Material.NOTE_BLOCK, "Sonido", () -> sounds(add, () -> show(menu)));
            menu.choice(Material.BLAZE_POWDER, "Partículas", () -> particles(add, () -> show(menu)));
            menu.choice(Material.POTION, "Efecto de poción", () -> potion(add, () -> show(menu)));
            text(menu, Material.GOLDEN_APPLE, "Curar", "heal", "Escribe los puntos de salud que se recuperan.", add);
        }
        show(menu);
    }

    private void sounds(BiConsumer<String, Object> add, Runnable back) {
        final Menu menu = new Menu("Sonidos", back);
        for (String key : Arrays.asList("entity.player.levelup", "entity.experience_orb.pickup",
                "entity.blaze.shoot", "entity.firework_rocket.blast", "block.note_block.pling", "block.amethyst_block.chime")) {
            menu.choice(Material.NOTE_BLOCK, key, () -> add.accept("sound", key));
        }
        text(menu, Material.PAPER, "Otro sonido", "sound", "Escribe la clave de Minecraft del sonido.", add);
        show(menu);
    }

    private void particles(BiConsumer<String, Object> add, Runnable back) {
        final Menu menu = new Menu("Partículas", back);
        Arrays.stream(Particle.values())
                .filter(particle -> particle.getDataType() == Void.class && particle.isRegistered())
                .sorted(Comparator.comparing(Enum::name))
                .forEach(particle -> menu.choice(Material.BLAZE_POWDER, particle.name(),
                        () -> add.accept("particle", particle.name())));
        show(menu);
    }

    private void potion(BiConsumer<String, Object> add, Runnable back) {
        final Menu menu = new Menu("Efectos de poción", back);
        for (String effect : Arrays.asList("speed", "strength", "regeneration", "resistance", "haste", "jump_boost",
                "night_vision", "fire_resistance", "water_breathing", "invisibility")) {
            menu.choice(Material.POTION, effect, () ->
                    input("Escribe la duración del efecto (por ejemplo 5s).", duration -> {
                        final Map<String, Object> config = new LinkedHashMap<>();
                        config.put("effect", effect);
                        config.put("duration", duration);
                        add.accept("potion", config);
                    }, () -> show(menu)));
        }
        show(menu);
    }

    private void text(Menu menu, Material icon, String label, String type, String hint, BiConsumer<String, Object> add) {
        menu.choice(icon, label, () -> input(hint, value -> add.accept(type, value), () -> show(menu)));
    }

    private void branch(Menu menu, Material icon, String label, String type, BiConsumer<String, Object> add) {
        menu.choice(icon, label, () -> {
            final Menu choices = new Menu(label, () -> show(menu));
            choices.choice(Material.LIME_DYE, "Sí", () -> add.accept(type, true));
            choices.choice(Material.RED_DYE, "No", () -> add.accept(type, false));
            show(choices);
        });
    }

    private void triggers(String title, Runnable back, Consumer<TriggerType> select) {
        final Menu menu = new Menu(title, back);
        for (TriggerType trigger : TriggerType.values()) {
            menu.choice(Material.LEVER, trigger.name(), () -> select.accept(trigger));
        }
        show(menu);
    }

    private void cooldown(WriteValue write, Runnable back) {
        final Menu menu = new Menu("Cooldown", back);
        for (String value : Arrays.asList("0s", "1s", "3s", "5s", "10s", "30s", "60s")) {
            menu.choice(Material.CLOCK, value.equals("0s") ? "Sin cooldown" : value,
                    () -> change(() -> write.accept(value), back));
        }
        menu.choice(Material.PAPER, "Duración personalizada", () ->
                input("Escribe una duración (por ejemplo 750ms o 10s).", write, back));
        show(menu);
    }

    private void input(String hint, WriteValue write, Runnable back) {
        final UiScreen previous = ItemEditor.INSTANCE.getUiManager().getScreen(player);
        final ChatPrompt prompt = new ChatPrompt(player, "§e" + hint, true) {
            @Override
            protected void onCancelled() {
                back.run();
            }

            @Override
            protected void onFulfilled(String input) {
                if (attempt(() -> write.accept(input))
                        && ItemEditor.INSTANCE.getUiManager().getScreen(player) != previous) {
                    return;
                }
                back.run();
            }

            @Override
            protected boolean validateInput(String input) {
                if (input.trim().isEmpty()) {
                    player.sendMessage("§cEscribe un valor.");
                    return false;
                }
                return true;
            }
        };
        Bukkit.getScheduler().runTask(engine.plugin(), () -> {
            if (player.isOnline() && ItemEditor.INSTANCE.getUiManager().getScreen(player) == previous
                    && previous.isInventory(player.getOpenInventory().getTopInventory())) {
                ItemEditor.INSTANCE.getPromptHandler().setPrompt(player, prompt);
            }
        });
    }

    private void confirm(String label, Write write, Runnable back) {
        show(new ConfirmUiScreen(player, "Confirmar eliminación", label, "Esta entrada se quitará de la configuración.",
                () -> change(write, back), back));
    }

    private void change(Write write, Runnable back) {
        attempt(write);
        back.run();
    }

    private boolean attempt(Write write) {
        if (!player.hasPermission(Permissions.WILDCARD)) {
            player.sendMessage("§cNo tienes permiso.");
            return false;
        }
        try {
            write.run();
            return true;
        } catch (ConfigException ex) {
            player.sendMessage("§c" + ex.getMessage());
        } catch (IOException ex) {
            player.sendMessage("§cNo se pudo guardar la configuración.");
            engine.plugin().getLogger().log(Level.WARNING, "Could not save item behavior", ex);
        }
        return false;
    }

    private void show(UiScreen screen) {
        ItemEditor.INSTANCE.getUiManager().open(player, screen);
    }

    private ConsumableManager consumables() {
        return engine.consumables();
    }

    private CustomItemManager customItems() {
        return engine.customItems();
    }

    private static ConfigException changed() {
        return new ConfigException("La configuración cambió. Vuelve a seleccionar la entrada.");
    }

    private static String seconds(long millis) {
        return millis == 0 ? "desactivado" : (millis / 1000.0) + " s";
    }

    @FunctionalInterface
    private interface Write {
        void run() throws ConfigException, IOException;
    }

    @FunctionalInterface
    private interface WriteValue {
        void accept(String value) throws ConfigException, IOException;
    }

    private enum Entries {
        COMMANDS("Comandos", Material.COMMAND_BLOCK),
        ACTIONS("Acciones", Material.BLAZE_POWDER),
        CONDITIONS("Condiciones", Material.COMPARATOR);

        final String title;
        final Material icon;

        Entries(String title, Material icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private final class Menu extends UiScreen {
        private static final int PAGE_SIZE = 45;
        private final List<Supplier<UiItem>> choices = new ArrayList<>();
        private final UiItem[] footer = new UiItem[9];
        private int page;

        Menu(String title, Runnable back) {
            super(BehaviorEditor.this.player, 54, title);
            button(49, Material.ARROW, "Volver", back);
        }

        void choice(Material icon, String title, Runnable action, String... lore) {
            choices.add(() -> new ActionUiItem(this, player, ItemBuilder.uiItem(icon, title, lore), action));
        }

        void button(int slot, Material icon, String title, Runnable action) {
            footer[slot - PAGE_SIZE] = new ActionUiItem(this, player, ItemBuilder.uiItem(icon, title), action);
        }

        @Override
        protected void onUpdate() {
            final int first = page * PAGE_SIZE;
            for (int i = first; i < Math.min(first + PAGE_SIZE, choices.size()); i++) {
                setItem(i - first, choices.get(i).get());
            }
            if (choices.size() > PAGE_SIZE) {
                final UiItem[] bar = PageBar.create(this, player, page, PageBar.pages(choices.size(), PAGE_SIZE), "", next -> {
                    page = next;
                    update();
                });
                setItem(47, bar[0]);
                setItem(48, bar[1]);
                setItem(50, bar[2]);
            }
            for (int i = 0; i < footer.length; i++) {
                if (footer[i] != null) {
                    setItem(PAGE_SIZE + i, footer[i]);
                }
            }
            fillEmptyWithFiller();
        }
    }
}
