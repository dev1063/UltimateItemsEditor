# UltimateItemsEditor

UltimateItemsEditor is an in-game item editor with a saved items manager (preview, get, give, edit, delete) and configurable item templates (language, enchantment and attribute styles). It is based on Item Editor 2 by Dumphey.

## Build from source

Use JDK 21 and Maven 3.9. From the directory containing `pom.xml`, run:

```sh
mvn clean package
```

Maven downloads the build dependencies and writes the plugin to `target/UltimateItemsEditor-2.1.0.jar`.

## Getting Started

To get started, put `UltimateItemsEditor-<version>.jar` in `plugins/`, restart your server and simply put an item in your hand and type `/ie edit`. That's the only command you'll need to edit an item - everything else can be achieved in the Item Editor menu.

## Features

- **Zero configuration**: Just drop the plugin in your `plugins/` directory, reload, and you are good to go.
- **Intuitive editing**: Rename, add lore, enchant, set attributes and item flags, change leather color, create custom potions, and more - all from the same menu.
- **Save items**: You can save (`/ie save`) and retrieve (`/ie get <name>`) any (created) items or give them to your players (`/ie give <player> <item> <amount>`).
- **Placeholders**: Give your players personalized items like '{player_name}'s axe' using [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/).
- ... and much more!

## Commands

- `/ie edit` - Edit the item you are holding.
- `/ie reopen` - Reopen the editor of the item you last edited.
- `/ie save <iname>` - Save the item you are holding.
- `/ie delete <iname>` - Delete a saved item.
- `/ie get <iname>` - Give yourself a saved item.
- `/ie give <player> <iname> <amount>` - Give X amount of an item to a player.
- `/ie list` - View a list of saved items.
- `/ie manage` - Open the saved items manager GUI: preview, get, give to a player, edit, apply a template and delete.
- `/ie template` - Pick a template for the item in your hand. Subcommands: `apply [name]`, `reset`, `list`, `inspect`, `reload`.
- `/ie language [name]` - Show or change the language used for template texts and messages.
- `/ie rules` - Trigger rules: `list`, `info <id>`, `reload`, `errors`, `stats`.
- `/ie tag` - Give the item in your hand a custom id or custom enchantment: `item <id|clear>`, `enchant <id> <level>`, `info`.
- `/ie consumable` - Items that run console commands when clicked and are used up: `create`, `update`, `addcmd`, `delcmd`, `addaction`, `delaction`, `set`, `give`, `info`, `list`, `delete`, `reload`, `errors`.
- `/ie customitem` (alias `citem`) - Items with skills: `create`, `update`, `give`, `info`, `list`, `delete`, `skill add|remove|action|condition|delaction|delcond|set`, `reload`, `errors`.

## Permissions

(A longer description will follow.)

For now, simply give yourself the `ie.*` permission to be able to use all features.

![Screenshot 1](https://i.imgur.com/S0VrUGA.png)
![Screenshot 2](https://i.imgur.com/dWYsCQg.png)
![Screenshot 3](https://i.imgur.com/emJwfTS.png)


## Templates, languages, enchantment and attribute styles

Everything is configurable in `plugins/UltimateItemsEditor/`:

| File | Purpose |
| --- | --- |
| `config.yml` | language, default template, global hide flags, decimals, keep item lore |
| `palettes.yml` (and `palettes/<name>.yml`) | the templates: name prefix, lore header/footer, tooltip flags, GUI icon |
| `enchantsstyle/vanilla.yml` | line format, display name and order of every vanilla enchantment |
| `enchantsstyle/custom.yml` | style for enchantments from other plugins (per namespace or per enchantment) |
| `enchantsstyle/attributes.yml` | line format, icon and order of every attribute |
| `messages/<language>.yml` | messages, enchantment/attribute names and `template-text.*` used by templates |

Templates are available from the editor home screen (**Template**), from the saved item manager (per item and
**Standardize all**) and with `/ie template`. A template keeps the item's own lore between its header and the
generated enchantment/attribute lines, never stacks when applied again, and `reset` restores the item.

Text formats: `&c`, `&#RRGGBB`, `<red>`, `<bold>`, `<#RRGGBB>`, `<gradient:#ff0000:#0000ff>..</gradient>`,
`<rainbow>..</rainbow>`. Put `%lang:rarity-epic%` in a template and it follows the active language. To add a
language, copy `messages/english.yml` to `messages/<yourlanguage>.yml`, translate it and run
`/ie language <yourlanguage>`.

## Upgrading from ItemEditor

The plugin is now called **UltimateItemsEditor** (`UltimateItemsEditor-<version>.jar`). Commands are unchanged:
`/ie` still works, together with `/uie`, `/itemeditor` and `/ultimateitemseditor`, and so does the `ie.*` permission.

1. Delete the old `ItemEditor-*.jar` from `plugins/`: both plugins would answer `/ie`.
2. Start the server. `plugins/ItemEditor/` (your saved items) is moved to `plugins/UltimateItemsEditor/` automatically.
   Items that already had a template keep working.

## Performance notes

- Saved items are kept in memory; saving and deleting write to disk on a background thread (and atomically, so a
  crash cannot corrupt an item). That thread only exists while it has something to write.
- Clicks and chat messages of players who are not using the plugin cost a single map lookup.
- The saved item list, its icons and the language list are cached, so opening or paging menus does no disk access.

## Triggers, conditions and actions

The base for custom items and custom enchantments. Rules live in `plugins/UltimateItemsEditor/rules/*.yml`
(`examples.yml` has one of everything, all disabled). Example:

```yaml
rules:
  fire_sword_ignite:
    trigger: ATTACK          # what the player does
    item: fire_sword         # only for items tagged with /ie tag item fire_sword
    cooldown: 3s
    conditions:              # all must be true (use any: / not: / all: to combine)
      - target-player: false
      - world: [world, world_nether]
      - chance: 40
    actions:                 # run in order
      - ignite: 4s
      - message: "&6Your sword sets %target% on fire!"
```

- **Triggers:** `INTERACT BLOCK_BREAK BLOCK_PLACE CONSUME DROP SHOOT_BOW ATTACK DEFEND KILL DEATH JOIN QUIT PERIODIC`.
- **Items and enchantments:** `item: <id>` binds a rule to a custom item, `enchant: <id>` to a custom enchantment
  (its level is `%level%`); `slots:` says where the item must be (hand, off_hand, head, chest, legs, feet, armor, any).
  Omit both to make a rule for everybody. The data is stored in the item's persistent data, so it survives anything.
- **Conditions:** world, click, slot, sneaking, sprinting, flying, gamemode, permission, chance, compare
  (`"damage >= 5"`), item-material, item-id, custom-enchant, vanilla-enchant, item-name-contains, item-lore-contains,
  block-type, target-type, target-player, time, cooldown, all, any, not.
- **Actions:** message, title, actionbar, particle, command, sound, potion, damage, heal, feed, ignite, set-damage, cancel, set-cooldown, delay, if.
  Durations are `5s`, `20t`, `2m`, `500ms`; a bare number is seconds.
- **Safe by design:** a typo is reported with its exact place (`/ie rules errors`) and the other rules still load; events
  another plugin already cancelled are skipped unless `run-when-cancelled: true`; a rule that keeps failing is
  switched off after `triggers.max-rule-errors` errors; a rule that triggers itself is cut after 4 levels.
- **Light by design:** with no rules there is no listener and no task. A listener exists only for the triggers your
  rules use, rules are looked up by trigger and item/enchantment id (no scanning), and an event whose items carry no
  custom data costs a single read.

### Adding your own pieces from code

```java
TriggerEngine engine = ItemEditor.INSTANCE.getTriggerEngine();
engine.registerCondition("has-mana", (config, pc) -> ctx -> manaOf(ctx.player) >= Cfg.number(config, pc));
engine.registerAction("lightning", (config, pc) -> ctx -> ctx.player.getWorld().strikeLightning(ctx.location()));
engine.load(); // rules can now use `has-mana: 20` and `lightning: true`
```

A future custom enchantment is then just data: a rule bound with `enchant: <id>`, plus `/ie tag enchant <id> <level>`
(or `ItemTags` from code) to put it on an item.

## Feedback for item actions

Use `message` for chat, `title` for the center of the screen, and `actionbar` for the text above the hotbar. Text supports
Minecraft colors and placeholders such as `%player%`. Use `sound` for Minecraft sound keys and `particle` for effects
such as `flame`, `heart` and `happy_villager`. Particles that require extra data, such as `dust` and `item`, are not
supported by this action.

Add these entries to `actions:` in a consumable, custom item skill or trigger rule:

```yaml
actions:
  - message: "&a%player%, reward granted!"
  - title: {text: "&6Reward", subtitle: "&fYou unlocked a bonus", fade-in: 10t, stay: 2s, fade-out: 10t}
  - actionbar: "&eBonus activated"
  - sound: {sound: entity.player.levelup, volume: 1, pitch: 1}
  - particle: {particle: happy_villager, count: 12, spread: 0.35, height: 1, speed: 0}
```

`target: self` is the default. On skills and rules with another entity involved, `target: target` addresses that entity.
Chat, titles and the action bar are sent to players. Sounds and particles originate at the selected entity.
A particle action emits at most 128 particles in one call. Use a cooldown or a suitable interval on frequent triggers.

## Consumable items

An item that, when clicked, runs console commands and is used up (vouchers, keys, ranks, money pouches...).

```
/ie consumable create vip_key                      (with the item in your hand: name, lore, texture... are copied)
/ie consumable create vip_key from my_saved_item   (or from a saved item)
/ie consumable addcmd vip_key lp user %player% parent add vip
/ie consumable addcmd vip_key eco give %player% 5000
/ie consumable addaction vip_key message &aReward granted to %player%!
/ie consumable addaction vip_key title &6VIP unlocked
/ie consumable addaction vip_key actionbar &eBonus activated
/ie consumable addaction vip_key sound entity.player.levelup
/ie consumable addaction vip_key particle happy_villager
/ie consumable set vip_key cooldown 2s             (also: click, amount, run-when-cancelled)
/ie consumable give vip_key Steve 5
```

Use `/ie consumable info vip_key` to see the action numbers and `/ie consumable delaction vip_key <number>` to remove
one. Consumables run their `commands:` first, then their `actions:` in order. If dispatching a command throws or the
server cannot find it, later feedback actions are skipped.

Each one is a file, `plugins/UltimateItemsEditor/consumables/<id>.yml`. Besides what the commands write you can add
`conditions:` and `actions:` by hand, with the same syntax as trigger rules (message, sound, potion, if, delay...):

```yaml
click: right            # left, right, any, left-air, left-block, right-air, right-block
amount: 1               # items taken per use
cooldown: 1s            # per player; stops holding the click from burning a whole stack
run-when-cancelled: false
commands:
- lp user %player% parent add vip
conditions:
- world: [world, survival]
actions:
- message: "&aYou are now VIP, %player%!"
- sound: "entity.player.levelup"
```

Placeholders: `%player% %world% %x% %y% %z% %item% (the consumable id)`. Run `/ie consumable reload` after editing a file.

- **Failed commands:** the item is returned if a synchronous action fails before a command has been dispatched.
  If a command already ran, the item stays consumed to avoid granting its reward again. The server logs the error.
- **Clicks:** when the click and conditions match, the consumable takes over the interaction.
- **Updating:** `/ie consumable update <id>` replaces the item; items given before keep working.
- **Light on the server:** no consumables means no listener. With some, there is one listener that discards a click with a
  single material lookup, before it reads anything from the item; only items of a consumable material have their mark read.

## The editor menu

`/ie edit` opens a menu organized in tabs:

```
[item]  [Appearance] [Properties] [Special items]  ...  [info]    the item (click = finish), tabs, item summary
=========================================================         a colored rail: the accent of the open tab
 the options of the open tab, 27 per page
[cancel]              [<] [page] [>]                [finish]
```

- Every option the editor has always had is still there: **Appearance** (name, lore, flags, templates), **Properties**
  (enchantments, attributes, durability, unbreakable) and **Special items** (potions, books, heads, leather armor).
- The menu remembers the tab and page: going back from a submenu returns to where you were.
- Texts follow `/ie language`; the click sound can be switched off with `gui.click-sounds: false` in `config.yml`.

### Adding your own option or tab

```java
EditorMenu menu = ItemEditor.INSTANCE.getEditorMenu();

// an option in an existing tab (order 10, 20, 30...: use 15 to go between the first two)
menu.registerOption("properties", "my_option", 50, MyOptionUiItem::new);   // (screen, player) -> a UiItem

// a new tab: category first, then its options
menu.registerCategory(new EditorCategory("custom", "Custom", "My own things",
        Material.NETHER_STAR, Material.GREEN_STAINED_GLASS_PANE, 40));
menu.registerOption("custom", "my_option", 10, MyOptionUiItem::new);
```

A tab with more than 27 options gets pages; with more than 7 tabs the last tab becomes a "more" button.
To translate a tab, add `gui-cat-<id>` and `gui-cat-<id>-desc` to the language files. Menus with their own pages use
`PageBar` (`ui/design`) so the controls look the same everywhere.

## Custom items with skills

An item can carry **skills**: triggers, conditions and actions that belong to the item (an active ability on right click,
an effect when it hits, a passive bonus while worn...). They run on the trigger engine, so they are indexed, error-isolated
and free when the item is not involved.

```
/ie customitem create fire_sword                         (the item in your hand: name, lore, texture... are copied)
/ie customitem skill add fire_sword ignite ATTACK ignite 4s
/ie customitem skill condition fire_sword ignite target-player false
/ie customitem skill action fire_sword ignite message &6Burn %target%!
/ie customitem skill action fire_sword ignite actionbar &cFlame Touch
/ie customitem skill action fire_sword ignite particle flame
/ie customitem skill set fire_sword ignite cooldown 3s
/ie customitem skill set fire_sword ignite cooldown-message &cWait %cooldown%s
/ie customitem give fire_sword Steve 2
```

Each item is a file, `plugins/UltimateItemsEditor/customitems/<id>.yml`; the commands edit it and refuse (saving nothing) any
change that would not compile. Edit it by hand for anything bigger, then `/ie customitem reload`:

```yaml
skills:
  ignite:                     # the skill id
    name: Flame Touch         # optional, for /ie customitem info
    trigger: ATTACK           # INTERACT, ATTACK, DEFEND, BLOCK_BREAK, KILL, PERIODIC...
    slots: [hand]             # where the item must be: hand, off_hand, head, chest, legs, feet, armor, any
    cooldown: 3s
    cooldown-message: "&cWait %cooldown%s"
    conditions:
      - target-player: false
      - chance: 40
    actions:
      - ignite: 4s
      - message: "&6Burn %target%!"
  warmth:                     # a passive skill: every 2 seconds while the item is worn on the feet
    trigger: PERIODIC
    slots: feet
    interval: 2s
    actions:
      - potion: { effect: speed, duration: 4s, amplifier: 0 }
```

Skills use the same triggers, conditions and actions as trigger rules (their ids are `<item>.<skill>`, so they show in
`/ie rules list`). Copies already given only carry the item's id: editing a skill changes every copy, `update` replaces the item
without touching the copies, and deleting the item turns them into ordinary items. A skill with an error is skipped and
reported with its exact place (`/ie customitem errors`); the item and its other skills keep working.

**Fast by design:** a skill only accepts the materials its item has (and has ever had), and when every item-bound rule of a
trigger lists its materials, the engine skips items of any other material without reading their data, also when it looks
at armor slots. (A rule bound to an item without `materials:` in `rules/*.yml` turns that shortcut off for its trigger.)

