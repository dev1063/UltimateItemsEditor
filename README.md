# UltimateItemsEditor

Editor de ítems para Minecraft Java. Permite personalizar y guardar ítems, crear consumibles y habilidades, y aplicar **59 encantamientos personalizados**. El editor se abre con `/ie edit`. Basado en Item Editor 2 de Dumphey.

- [Instalación y actualización](#instalación-y-actualización)
- [Módulos y menús](#módulos-y-menús)
- [Encantamientos personalizados](#encantamientos-personalizados)
- [Adquisición, libros y rarezas](#adquisición-libros-y-rarezas)
- [Rendimiento](#rendimiento)
- [Consumibles, habilidades y reglas](#consumibles-habilidades-y-reglas)
- [Comandos y permisos](#comandos-y-permisos)
- [Archivos y estilos](#archivos-y-estilos)
- [Compilación y extensiones](#compilación-y-extensiones)

## Instalación y actualización

1. Usa **Java 21** y un servidor compatible con la **API de Spigot 1.21.4**, que es la versión contra la que se compila el proyecto.
2. Coloca `UltimateItemsEditor-2.1.0.jar` en `plugins/` e inicia el servidor. El plugin crea `plugins/UltimateItemsEditor/`.
3. Da el permiso `ie.*` a los administradores que usarán el editor. En el juego, sostén un ítem y ejecuta `/ie edit`.

**PlaceholderAPI** es opcional. Si está instalado, el editor puede resolver sus placeholders en nombres y lore.

Al actualizar, reemplaza el JAR y conserva la carpeta de datos. Las opciones nuevas tienen valores predeterminados aunque tu `config.yml` sea anterior. La migración del catálogo añade los campos de rareza nuevos y conserva los valores configurados en el servidor. Puedes consultar las claves nuevas en el [config.yml incluido](src/main/resources/config.yml).

Si vienes de **Item Editor 2**, retira su JAR para evitar el conflicto por `/ie`. En el primer arranque, el plugin intenta migrar `plugins/ItemEditor/` a `plugins/UltimateItemsEditor/`. Si la carpeta de destino ya contiene archivos, la migración se omite.

## Módulos y menús

| Opción en `config.yml` | Predeterminado | Función |
| --- | --- | --- |
| `modules.vanilla` | `true` | Pestañas de aspecto, propiedades e ítems especiales. Incluye nombres, lore, encantamientos vanilla, atributos, durabilidad, pociones, libros, cabezas, cuero y plantillas. |
| `modules.custom-commands` | `true` | Pestaña **Comportamiento**, consumibles, ítems con habilidades y reglas generales o vinculadas a ítems. |
| `modules.custom-enchantments` | `false` | Catálogo, aplicación, adquisición y reglas de encantamientos personalizados. |

Reinicia el servidor al cambiar un módulo. Los ítems guardados y el idioma siguen disponibles con los tres módulos desactivados. `triggers.enabled`, `consumables.enabled` y `customitems.enabled` controlan funciones dentro de los módulos.

- **Editar lo que sostienes:** `/ie edit`. El editor recuerda la pestaña y página. Usa **Finalizar** para devolver el ítem editado a la mano.
- **Administrar ítems guardados:** `/ie save <nombre>` guarda una copia. `/ie manage` o `/ie list` abre la lista paginada para previsualizar, obtener, entregar, editar o borrar. Las plantillas se pueden aplicar a un ítem o a toda la lista cuando el módulo vanilla está activo.
- **Configurar comportamiento:** en **Comportamiento**, elige **Ítems personalizados** o **Consumibles**. Puedes crear una definición a partir de una copia del ítem editado o elegir una existente. Las definiciones se guardan al aplicar cada cambio.
- **Configurar sonidos de la interfaz:** `gui.click-sounds` controla el sonido de clic.

## Encantamientos personalizados

1. Activa `modules.custom-enchantments: true` y reinicia. Deja `triggers.enabled: true` para ejecutarlos.
2. Abre **`/ie edit` → Encantamientos custom → Catálogo de encantamientos**. El menú muestra las opciones compatibles con el material y los encantamientos ya aplicados.
3. Elige el nivel o quita uno existente. También puedes ejecutar `/ie tag enchant <id> <nivel>` con el ítem en la mano. El nivel `0` elimina la etiqueta. `/ie tag info` muestra las etiquetas instaladas.

El [catálogo configurable](src/main/resources/custom-enchantments.yml) define los nombres, materiales, ranuras, activadores, probabilidades, cooldowns y niveles. Cada entrada acepta hasta 16 niveles consecutivos desde el nivel 1. Usa `enabled: false` para desactivar una entrada, `/ie rules reload` para recargar y `/ie rules errors` para revisar entradas inválidas.

<details>
<summary>Ver los 59 encantamientos y sus identificadores</summary>

| Encantamiento | ID | Rareza base | Niveles | Efecto |
| --- | --- | --- | ---: | --- |
| **Pacto de sangre** | `blood_pact` | Raro | 3 | Cura al golpear cuerpo a cuerpo con una espada. |
| **Filo de brasas** | `ember_edge` | Poco común | 3 | Incendia al objetivo con golpes de espada. |
| **Guardia férrea** | `iron_ward` | Raro | 3 | El peto reduce el daño recibido en combate. |
| **Paso veloz** | `swiftstep` | Raro | 2 | Las botas dan velocidad al esprintar. |
| **Enfoque minero** | `miner_focus` | Poco común | 3 | El pico da prisa al romper bloques. |
| **Fortuna de la experiencia** | `fortune_xp` | Raro | 3 | Multiplica la XP obtenida al minar o matar. |
| **Pacto del nigromante** | `necromancer` | Épico | 1 | Puede invocar un esqueleto o zombi contra una criatura atacante. |
| **Quebramontañas** | `tunneler` | Épico | 4 | Excava planos de 3×3 a 6×6 con pico o pala. |
| **Pulso de la veta** | `veinminer` | Épico | 4 | Extrae menas conectadas del mismo mineral con un pico. |
| **Lluvia de filos** | `multishot` | Legendario | 4 | Añade flechas no recuperables al usar arco, ballesta o tridente. |
| **Centinelas de acero** | `steel_guardians` | Legendario | 1 | Puede invocar un gólem contra una criatura atacante. |
| **Flecha del vacío** | `end_arrow` | Épico | 1 | Teletransporta a un lugar seguro cerca del impacto de la flecha. |
| **Salto del cometa** | `hare` | Poco común | 3 | Las botas dan Salto I–III. |
| **Senda de la caldera** | `magma_step` | Épico | 2 | Solidifica temporalmente lava expuesta y magma bajo las botas. |
| **Quebracristales** | `glass_shatter` | Común | 1 | Rompe al instante cristal y paneles con un pico. |
| **Titán del bosque** | `tree_feller` | Épico | 4 | Tala troncos conectados del mismo tipo con un hacha. |
| **Imán del saqueador** | `auto_collect` | Raro | 1 | Envía los drops al inventario y deja el sobrante en el suelo. |
| **Flecha Cataclismo** | `blast_arrow` | Épico | 1 | Explota al impactar y daña a entidades cercanas. |
| **Anzuelo de Hidra** | `toxic_hook` | Poco común | 3 | Envenena al jugador enganchado con la caña. |
| **Anzuelo del Averno** | `ember_hook` | Poco común | 3 | Incendia al jugador enganchado con la caña. |
| **Aguijón Esmeralda** | `poison_arrow` | Poco común | 1 | Envenena al objetivo al impactar. |
| **Colmillo de Víbora** | `viper` | Poco común | 3 | Puede envenenar con golpes de espada. |
| **Filo del Invierno Eterno** | `frostblade` | Raro | 3 | Puede congelar y ralentizar con golpes de espada. |
| **Corte del Eclipse** | `blindblade` | Poco común | 3 | Puede cegar con golpes de espada. |
| **Proyectil del Ocaso** | `blind_arrow` | Poco común | 1 | Da a la flecha una posibilidad de cegar al impactar. |
| **Veredicto del Trueno** | `thunderblade` | Raro | 3 | Puede mostrar un rayo y añadir daño al golpe de espada. |
| **Cisma del Caos** | `disorder` | Épico | 1 | Puede desordenar la barra rápida del rival. |
| **Ladrón de Corazas** | `disarmer` | Legendario | 1 | Puede mover una pieza de armadura del rival a su inventario si tiene espacio. |
| **Forja del Sol** | `autocook` | Raro | 1 | Puede fundir los drops compatibles con recetas de horno. |
| **Acero de la Marchitez** | `venom_blade` | Épico | 3 | Puede aplicar Wither con golpes de espada. |
| **Arco de la Marchitez** | `venom_bow` | Épico | 1 | Da a la flecha una posibilidad de aplicar Wither. |
| **Forja Demoníaca** | `demon_reforged` | Épico | 3 | Puede reparar la espada al matar a otro jugador. |
| **Último Latido** | `ultimate_life` | Raro | 3 | Puede reparar el equipo al recibir daño de durabilidad que lo rompería. |
| **Tributo al Vacío** | `binding` | Común | 1 | Puede hacer desaparecer el ítem equipado al morir. |
| **Segador de Coronas** | `beheading` | Raro | 1 | Puede soltar la cabeza de un enemigo compatible al matarlo. |
| **Furia del Titán** | `berserker` | Raro | 3 | Puede otorgar fuerza al golpear a un jugador con un hacha. |
| **Égida del Viento** | `aegis` | Común | 3 | Puede otorgar velocidad al sufrir daño por caída. |
| **Gravedad del Abismo** | `mob_pull` | Común | 3 | Atrae hacia ti a la criatura golpeada. |
| **Nova de Cacería** | `mob_nova` | Raro | 3 | Daña a criaturas cercanas en un radio que sube con el nivel. |
| **Escudo de Adamantio** | `sword_ward` | Épico | 4 | Reduce el daño de espadas enemigas un 2 % por nivel. |
| **Paso Fantasma** | `arrow_dodge` | Épico | 1 | Puede evitar el daño de una flecha enemiga. |
| **Pacto de la Mella** | `weapon_sacrifice` | Poco común | 3 | Repara la espada dañada a cambio de reducir el daño del golpe. |
| **Marca Carmesí** | `bleed` | Raro | 3 | Puede causar sangrado mediante tres pulsos de daño. |
| **Siembra de la Aurora** | `carrot_planter` | Común | 1 | Planta zanahorias en 3×3 con un azadón y clic derecho al agacharse. |
| **Piel del Coloso** | `damage_guard` | Poco común | 3 | Puede reducir el daño recibido. |
| **Ruina del Duelo** | `player_nova` | Épico | 3 | Daña a jugadores cercanos en un radio que sube con el nivel. |
| **Vértigo del Vacío** | `nausea` | Poco común | 3 | Puede aplicar náuseas a un jugador golpeado. |
| **Cólera del Vendaval** | `air_launch` | Raro | 3 | Puede lanzar a un atacante al aire. |
| **Perdición del End** | `ender_slayer` | Común | 3 | Aumenta el daño de espada a endermen y al dragón del End. |
| **Purga de la Luz** | `cleanse` | Raro | 1 | Puede eliminar efectos de poción negativos. |
| **Legión de la Mecha** | `creeper_guard` | Legendario | 1 | Puede invocar un creeper guardián cuando un ataque te deja cerca de morir. |
| **Bendición de la Lluvia** | `extinguish` | Común | 1 | Puede apagar al jugador si está en llamas. |
| **Estela de Supernova** | `rocket_boost` | Raro | 3 | Puede aumentar el impulso de cohetes al planear con élitros. |
| **Venganza de Salamandra** | `retaliation_fire` | Poco común | 3 | La armadura puede incendiar al atacante. |
| **Cazador de Medianoche** | `night_hunter` | Poco común | 3 | Aumenta el daño de espada a criaturas durante la noche. |
| **Espejo del Némesis** | `reflect` | Legendario | 3 | Puede cancelar el golpe y devolver daño aumentado al atacante. |
| **Renacer del Fénix** | `revive` | Legendario | 3 | La armadura puede evitar un golpe letal y recuperar salud. |
| **Alas del Vendaval** | `glide_boost` | Épico | 3 | Impulsa los élitros con clic derecho mientras planeas. |
| **Sentencia Final** | `critical_finish` | Legendario | 1 | Puede infligir un golpe de remate a un objetivo con menos de 5 puntos de vida. |

</details>

Los encantamientos usan etiquetas persistentes del plugin. El catálogo toma como referencia la organización por niveles, equipo y eventos de [ExcellentEnchants](https://github.com/nulli0n/ExcellentEnchants-spigot).

## Adquisición, libros y rarezas

Mesa y bibliotecarios se activan por separado. Coloca estos ajustes en `plugins/UltimateItemsEditor/config.yml` y ejecuta `/ie rules reload`. Si el módulo de encantamientos estaba desactivado, actívalo y reinicia primero.

```yaml
custom-enchant-acquisition:
  table:
    enabled: false
    chance-percent: 20
    rarity-weights: {common: 60, uncommon: 30, rare: 12, epic: 4, legendary: 1}
  villagers:
    enabled: false
    chance-percent: 20
    rarity-weights: {common: 45, uncommon: 27, rare: 15, epic: 7, legendary: 2}
  inventory:
    enabled: true
    success-percent: 70
    destruction-percent: 30
    destruction-mode: enchantment
  anvil:
    enabled: true
    base-level-cost: 3
```

### Mesa y bibliotecarios

- **Mesa:** un encantamiento vanilla exitoso puede añadir un encantamiento custom compatible. El ítem conserva sus encantamientos vanilla y la mesa cobra el lapislázuli y la experiencia habituales. Las tres ofertas limitan el nivel custom a I, II o III respectivamente.
- **Bibliotecarios:** al generar una nueva oferta de libro encantado, el plugin puede sustituirla por un libro custom y conserva los ingredientes, las existencias y la mecánica de precios de la oferta original. El nivel del aldeano limita el del libro. Desactivar la opción bloquea la compra de libros custom ya ofertados mientras la integración esté cargada. Los libros comprados siguen disponibles para aplicar.

### Aplicar un libro

**Inventario:** abre tu inventario personal, toma el libro con el cursor y haz **clic derecho** sobre un único ítem compatible. Un libro incompatible o de nivel igual o inferior se conserva.

| Resultado | Qué ocurre |
| --- | --- |
| Éxito | Se aplica el encantamiento y se consume el libro. |
| Destrucción en modo `enchantment` | Se destruye el libro y conservas el ítem con sus encantamientos anteriores. |
| Destrucción en modo `item` | Se destruyen el libro y el ítem. |
| Fallo sin destrucción | Conservas el libro y el ítem. Es el porcentaje que queda hasta 100. |

Una sola tirada decide el resultado. Si éxito y destrucción suman más de 100, el plugin reduce la probabilidad efectiva de destrucción y lo registra en consola.

**Yunque:** coloca el ítem a la izquierda y el libro a la derecha. Si el nivel mejora el ítem, el resultado consume el libro y cobra el coste base más `nivel - 1` y la penalización por trabajos previos. Pon `anvil.enabled: false` si quieres exigir la tirada del inventario a todos los libros custom.

### Rareza por encantamiento y nivel

En `custom-enchantments.yml`, cada entrada define `rarity` y puede especificar valores distintos con `level-rarities`:

```yaml
# Dentro de una entrada de enchantments con esos niveles definidos
rarity: rare
level-rarities:
  '2': epic
  '3': legendary
```

Las rarezas válidas son `common`, `uncommon`, `rare`, `epic` y `legendary`. Los niveles sin valor propio heredan la rareza del encantamiento. Después de superar la probabilidad de adquisición, cada fuente sortea primero el encantamiento según su rareza y luego el nivel según su propio peso. Los pesos son relativos a las opciones elegibles y **0** excluye esa rareza de la fuente. La rareza se muestra en el editor y en los libros de nueva generación.

## Rendimiento

- Las reglas se indexan por evento e ID. El motor filtra por ranura y material antes de leer los metadatos y reutiliza etiquetas ya interpretadas.
- Los efectos opcionales costosos, como explosiones, daño de área, invocaciones, proyectiles extra y cambios de terreno, comparten una cola y un presupuesto por tick. Los efectos pendientes pueden caducar bajo saturación. `/ie rules stats` muestra cuántos se descartaron.
- Las reglas periódicas distribuyen los jugadores entre varios ticks del intervalo. La excavación tiene su propio presupuesto de **24 bloques candidatos por tick**. Omite chunks descargados y usa los eventos normales de rotura. Agáchate para minar un solo bloque.
- La extracción de vetas tiene un límite de 48 bloques adicionales y la tala de 96. Las explosiones de flechas y creepers guardianes afectan a entidades. El daño de área está limitado a ocho objetivos cercanos y evita encadenar más efectos de combate del catálogo.
- El gestor conserva los ítems guardados en memoria y escribe las operaciones de guardado y borrado en segundo plano.

| Opción en `enchantment-performance` | Predeterminado | Qué ajusta |
| --- | --- | --- |
| `work-per-tick` | `64` | Presupuesto ponderado de trabajo por tick. Acepta 32–256. |
| `max-queued-effects` | `256` | Capacidad de la cola. Acepta 16–1024. |
| `periodic-spread-ticks` | `8` | Ticks en los que se reparten las comprobaciones. Acepta 1–20, hasta el intervalo base. |

Recarga con `/ie rules reload`. Los límites controlan el trabajo programado por el plugin. El impacto en MSPT depende del equipo, la población y los demás plugins.

## Consumibles, habilidades y reglas

### Consumibles

En **Comportamiento → Consumibles** puedes elegir el clic de activación, la cantidad gastada y el cooldown, y añadir o eliminar condiciones y acciones. Puedes editar texto y comandos desde el chat. La entrada de comandos en la GUI se escribe sin la barra inicial.

```text
/ie consumable create recompensa
/ie consumable addcmd recompensa say %player% ha usado una recompensa
/ie consumable addaction recompensa title &6Recompensa
/ie consumable addaction recompensa particle happy_villager
/ie consumable set recompensa cooldown 2s
/ie consumable give recompensa Steve 5
```

El consumible ejecuta sus comandos y luego sus acciones, en orden. Si una ejecución síncrona falla antes del primer comando despachado, devuelve el ítem. Si ya despachó un comando, el ítem permanece consumido para evitar repetir una recompensa. `/ie consumable info <id>` muestra los índices de las acciones y los comandos.

### Ítems y habilidades

En **Comportamiento → Ítems personalizados**, crea una definición y añade habilidades con activadores y cooldowns propios. Puedes añadir o quitar condiciones, comandos y efectos. Si quitas la última acción de una habilidad, queda desactivada hasta que añadas otra y vuelvas a habilitarla. El icono de cofre entrega una copia configurada.

```text
/ie customitem create espada_ardiente
/ie customitem skill add espada_ardiente fuego ATTACK ignite 4s
/ie customitem skill condition espada_ardiente fuego target-player false
/ie customitem skill action espada_ardiente fuego actionbar &6Llama activada
/ie customitem skill set espada_ardiente fuego cooldown 3s
/ie customitem give espada_ardiente Steve 1
```

### Reglas y acciones

Los archivos de `rules/` vinculan reglas a `item: <id>` o `enchant: <id>`. Una regla sin vínculo aplica de forma general. Usa `slots` para seleccionar el equipo que puede activarla y `materials` para filtrar el material antes de leer sus etiquetas.

```yaml
rules:
  fuego_al_golpear:
    trigger: ATTACK
    item: espada_ardiente
    slots: hand
    cooldown: 3s
    conditions:
      - target-player: false
      - chance: 40
    actions:
      - ignite: 4s
      - actionbar: "&6Arde %target%"
      - sound: {sound: entity.blaze.shoot, volume: 1, pitch: 1}
      - particle: {particle: flame, count: 10}
```

- **Eventos:** `INTERACT`, `BLOCK_BREAK`, `BLOCK_DAMAGE`, `BLOCK_PLACE`, `FISH_HOOK`, `ITEM_WEAR`, `CONSUME`, `DROP`, `SHOOT_BOW`, `THROW_TRIDENT`, `ATTACK`, `DEFEND`, `INJURY`, `FLIGHT_USE`, `KILL`, `DEATH`, `JOIN`, `QUIT` y `PERIODIC`.
- **Condiciones:** mundo, clic, ranura, estado del jugador, permiso, probabilidad, comparación de valores, datos del ítem y tipo de objetivo. Combina condiciones con `all`, `any` y `not`. Consulta los [ejemplos de reglas](src/main/resources/rules/examples.yml).
- **Acciones:** `message` para chat, `title` para título y subtítulo, `actionbar` para la barra rápida, `sound`, `particle`, `command`, `potion`, `damage`, `heal`, `feed`, `ignite`, `set-damage`, `cancel`, `set-cooldown`, `delay` e `if`. `target: self` actúa sobre el jugador y `target: target` sobre el objetivo del evento, si existe.
- **Duraciones y textos:** `5s`, `20t`, `2m` o `500ms`. Un número sin unidad representa segundos. Los textos y comandos admiten placeholders como `%player%`, `%target%`, `%level%` y `%cooldown%`.
- **Diagnóstico:** `/ie rules errors` muestra reglas inválidas. Las reglas con errores repetidos se desactivan según `triggers.max-rule-errors`. Por defecto, las reglas omiten los eventos que otros plugins cancelaron.

## Comandos y permisos

El permiso administrativo es **`ie.*`**. `/ie`, `/uie` y `/itemeditor` son alias de `/ultimateitemseditor`.

| Comando | Función |
| --- | --- |
| `/ie edit`, `/ie reopen` | Abrir el editor o reabrir el último ítem. |
| `/ie save <nombre>`, `/ie get <nombre>`, `/ie delete <nombre>` | Guardar, obtener o borrar una entrada. |
| `/ie give <jugador> <ítem> <cantidad>` | Entregar un ítem guardado. |
| `/ie manage`, `/ie list [plain]` | Abrir el gestor o listar los ítems guardados. |
| `/ie template [apply\|reset\|list\|inspect\|reload]` | Configurar plantillas para el ítem o consultar las disponibles. |
| `/ie language [idioma]` | Consultar o cambiar el idioma. |
| `/ie rules <list\|info\|reload\|errors\|stats>` | Gestionar reglas, recargar la configuración y consultar el rendimiento. |
| `/ie tag <item\|enchant\|info>` | Etiquetar o inspeccionar el ítem sostenido. |
| `/ie consumable <subcomando>` | Crear, editar, entregar o administrar consumibles. |
| `/ie customitem <subcomando>` | Crear, editar, entregar o administrar ítems con habilidades. También `/ie citem`. |

## Archivos y estilos

Las rutas de esta tabla son relativas a `plugins/UltimateItemsEditor/`.

| Ruta | Contenido |
| --- | --- |
| `config.yml` | Módulos, idioma, plantillas, adquisición y rendimiento. |
| `custom-enchantments.yml` | Catálogo, rarezas, niveles y efectos. |
| `rules/*.yml` | Reglas adicionales para eventos. |
| `consumables/<id>.yml`, `customitems/<id>.yml` | Definiciones de consumibles e ítems con habilidades. |
| `items/` | Ítems guardados desde el editor y los comandos. |
| `palettes.yml`, `palettes/*.yml` | Plantillas, prefijos, encabezados, pies de lore e iconos. |
| `enchantsstyle/vanilla.yml`, `enchantsstyle/custom.yml`, `enchantsstyle/attributes.yml` | Formatos y orden de encantamientos y atributos. |
| `messages/<idioma>.yml` | Mensajes y textos de plantillas. |

Las plantillas pueden conservar el lore original según `keep-item-lore`. Volver a aplicar una plantilla reemplaza sus líneas generadas. `/ie template reset` recupera el estilo original almacenado. Los formatos incluyen `&c`, `&#RRGGBB`, `<bold>`, gradientes y arcoíris. Para añadir un idioma, copia un archivo de `messages/`, tradúcelo y selecciónalo con `/ie language <idioma>`.

## Compilación y extensiones

Abre `pom.xml` en IntelliJ IDEA como proyecto Maven y selecciona **JDK 21**. Con Maven 3.9, ejecuta:

```sh
mvn clean package
```

El JAR se genera en `target/UltimateItemsEditor-2.1.0.jar`.

- **Editor:** `ItemEditor.INSTANCE.getEditorMenu()` permite registrar categorías con `registerCategory` y botones con `registerOption`. Para traducir una pestaña, usa `gui-cat-<id>` y `gui-cat-<id>-desc` en los mensajes. Los submenús pueden reutilizar `PageBar`.
- **Reglas:** con un módulo de reglas activo, `ItemEditor.INSTANCE.getTriggerEngine()` expone `registerCondition` y `registerAction`. Registra las extensiones antes de recargar las definiciones para que puedan compilarlas.
