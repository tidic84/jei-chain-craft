# JEI Chain Craft

NeoForge 1.21.1 addon for JEI.

Hover any item, press `C`. A recursive crafting chain opens — what's already in your inventory, what's still missing, in what order to craft. On a crafting table, click *Execute chain* and it runs the whole thing for you.

The point: modpacks where figuring out "what do I actually need to farm" takes ten clicks through JEI.

## Use

- Press `C` while hovering an item. Works on:
  - any slot in your inventory or in a container,
  - JEI recipe views,
  - JEI's right-side ingredient list,
  - JEI's bookmarks.
- The quantity field is what you want to **craft**, not the total to have. `qty = 2` with one already in your pack means crafting 2 more, ending with 3.
- Open a crafting table and click *Execute chain*. The mod sends the same packet vanilla's recipe book sends, so it works on any server — no server-side mod required.
- Click *Pin* to copy the base-resource list onto an HUD overlay (top-left). Counts update live as you collect items; a row goes grey + struck through when it's satisfied.

A `+N` badge next to a node means there are other recipes producing that item. Click the badge to choose one — the choice sticks for that item until you close the game (or click *Reset prefs*).

The hotkey is rebindable in Options → Controls → JEI Chain Craft.

## Scope

Vanilla crafting only — shaped and shapeless, 2x2 or 3x3.

Not included by design: smelting, blasting, smoking, campfire cooking, stonecutting, smithing. The tool is for *recursive crafting*, not for automating every transformation in the game. Ores that need a furnace stay as leaves with status MISSING.

Modded machines aren't supported out of the box either, but the public registry `CraftHandlerRegistry.register(handler)` lets other mods plug in their own container menus. A handler is just two methods: place the ingredients, take the output. The executor does the timing. Extra item sources (storage mods) plug in through `InventoryAnalyzer.registerSource`.

## Planner semantics

- Every build runs against a single inventory **budget**: branches reserve what they consume, so two branches can never count the same stack twice. Whole-craft rounding surplus is credited back for later steps.
- Tag ingredients match whatever variant you actually own (`#planks` accepts birch); identical ingredient slots are merged before counting.
- If the chosen recipe for an item dead-ends — a cycle like iron ingot ⇄ iron block, or missing ingredients — the other recipes producing that item are tried before the branch is declared MISSING.
- The executor only takes the output after verifying it actually appeared; if it never does, the run aborts with an error instead of clicking through the rest of the plan.

## Tom's Simple Storage

Optional integration, active when `toms_storage` is installed:

- Planning from an open terminal (storage or crafting variant) counts everything in the network, not just your pockets.
- *Execute chain* works in the crafting terminal; ingredients are pulled from the network by Tom's own recipe placer.
- Intermediate crafts are pushed back into the network as each step finishes (if the network is full, the remainder stays in your inventory). The final target stays with you.

## Known gaps

- Recipe preferences are in-memory. They don't survive a game restart.
- Execution stops if you close the crafting menu mid-run. Re-open and click Execute again to resume from where the planner left off (it walks the same tree, so already-crafted items now show as HAVE).

## Versions

- NeoForge 21.1.228
- JEI 19.27.0.340
- Minecraft 1.21.1

## Build

JDK 21.

```
./gradlew build
```

Jar in `build/libs/`. Drop it next to JEI in your `mods/`.

## License

MIT. See `LICENSE`.
