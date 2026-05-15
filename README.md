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

Modded machines aren't supported out of the box either, but the public registry `CraftHandlerRegistry.register(handler)` lets other mods plug in their own container menus. A handler is just two methods: place the ingredients, take the output. The executor does the timing.

## Known gaps

- Recipe preferences are in-memory. They don't survive a game restart.
- Tag ingredients use the first item in the tag for inventory checks. If a recipe needs `#planks` and you have birch but the algorithm picked oak as canonical, the node shows MISSING. On the to-do list.
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
