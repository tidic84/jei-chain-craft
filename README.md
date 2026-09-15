# JEI Chain Craft

Minecraft 26.2 addon for JEI, on NeoForge and Fabric.

Branches: `main` targets the latest Minecraft version (26.2). Older versions live on their own branch (`1.21.1`).

Hover any item, press `C`. A recursive crafting chain opens — what's already in your inventory, what's still missing, in what order to craft. On a crafting table — or a storage-network crafting terminal (Tom's Simple Storage, Refined Storage) — click *Execute chain* and it runs the whole thing for you.

The point: modpacks where figuring out "what do I actually need to farm" takes ten clicks through JEI.

## Use

- Press `C` while hovering an item. Works on:
  - any slot in your inventory or in a container,
  - JEI recipe views,
  - JEI's right-side ingredient list,
  - JEI's bookmarks.
- The quantity field is what you want to **craft**, not the total to have. `qty = 2` with one already in your pack means crafting 2 more, ending with 3.
- Open a crafting table (or a supported crafting terminal, see [Compatibility](#compatibility)) and click *Execute chain*. Ingredients are placed through JEI's recipe transfer (the same action as JEI's `+` button), so nothing beyond JEI is needed on the server. Crafts are batched, so long chains take seconds, not minutes.
- Click *Pin* to copy the base-resource list onto an HUD overlay (top-left). Counts update live as you collect items; a row goes grey + struck through when it's satisfied.

A `+N` badge next to a node means there are other recipes producing that item. Click the badge to choose one — the choice sticks for that item until you close the game (or click *Reset prefs*).

The hotkey is rebindable in Options → Controls → JEI Chain Craft.

## Compatibility

Optional integrations, detected at runtime. None of these mods is required: without them, the mod just skips the integration.

| Mod | Supported menu | What you get |
| --- | --- | --- |
| [Tom's Simple Storage](https://www.curseforge.com/minecraft/mc-mods/toms-storage) | Crafting Terminal | Execute chains from the terminal. Ingredients come from the storage network, crafted items go back into it. The tree counts items stored in the network. |
| [Refined Storage 2](https://www.curseforge.com/minecraft/mc-mods/refined-storage) | Crafting Grid | Same as Tom's: execute from the grid, ingredients pulled from the network (then your inventory), results sent back, network stock counted in the tree. |

Notes:
- On storage terminals, batch size is limited by what the network holds, so the crafted count stays exact even when ingredients run low.
- Refined Storage: only the Crafting Grid is supported (the plain and portable grids have no crafting). Items with components (enchantments, custom data…) are not counted in the network stock.
- If a mod renames its menu classes, the integration silently stops matching and *Execute chain* reports that no handler was found.

## Scope

Crafting recipes only — shaped and shapeless, 2x2 or 3x3.

Not included by design: smelting, blasting, smoking, campfire cooking, stonecutting, smithing. The tool is for *recursive crafting*, not for automating every transformation in the game. Ores that need a furnace stay as leaves with status MISSING.

Modded machines aren't supported out of the box either, but the public registry `CraftHandlerRegistry.register(handler)` lets other mods plug in their own container menus. A handler only needs two methods — place the ingredients, take the output — and can optionally opt into batching. The executor does the timing. The Tom's and Refined Storage handlers in `executor/handlers/compat/` are working examples.

## Known gaps

- JEI must also be installed on the server (always the case in singleplayer). Since Minecraft 1.21.2 the server no longer sends recipes to the client; JEI syncs them, and the mod reads and places recipes through JEI.
- Recipe preferences are in-memory. They don't survive a game restart.
- Tag ingredients (e.g. `#planks`) default to an item you already own, else the first item of the tag. Click the `#N` badge on the node to pick another one; like recipe choices, this is in-memory only.
- Execution stops if you close the crafting menu mid-run. Re-open and click Execute again to resume from where the planner left off (it walks the same tree, so already-crafted items now show as HAVE).

## Versions

- Minecraft 26.2 (Java 25)
- JEI 30.32.0.221

| Loader | Version |
| --- | --- |
| NeoForge | 26.2.0.88 |
| Fabric | Loader 0.19.5, Fabric API 0.160.0+26.2 |

For Minecraft 1.21.1, use the `1.21.1` branch.

Integrations were reverse-engineered against Tom's Simple Storage 1.21-2.3.2 and Refined Storage (NeoForge) 2.0.9. On 26.2, placement goes through each mod's own JEI transfer handler; the network-stock readers have not been re-checked against their 26.x releases.

## Build

JDK 25.

```
./gradlew build
```

One jar per loader:
- NeoForge: `neoforge/build/libs/`
- Fabric: `fabric/build/libs/`

(The `-sources` jars are not mods.)

Drop the one matching your loader next to JEI in your `mods/`. Fabric also needs Fabric API.

Project layout (same approach as JEI and the MultiLoader template — one branch per Minecraft version, all loaders in that branch):
- `common/` — all the mod logic, compiled against vanilla Minecraft only.
- `neoforge/`, `fabric/` — entry points that wire `common` to each loader's events.

Dev clients: `./gradlew :neoforge:runClient` (game dir `neoforge/run-neoforge/`) and `./gradlew :fabric:runClient` (game dir `fabric/run-fabric/`).

## License

MIT. See `LICENSE`.
