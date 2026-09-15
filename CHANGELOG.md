# Changelog

## 0.3.0

### Added
- **Refined Storage 2 compatibility**: the executor now works from the Refined
  Storage crafting grid. Ingredients are placed through RS's own recipe
  transfer (network first, then player inventory), and crafted items are sent
  back into the network. No hard dependency: the mod loads normally without RS.
  - Shaped recipes keep their layout in the grid.
  - A tag ingredient chosen in the picker is the only one sent to RS;
    otherwise RS uses the alternative it has the most of.
- **Network stock awareness for Refined Storage**: while an RS grid is open,
  the tree counts items stored in the network in addition to the player
  inventory.
- Batched crafting also applies to the RS crafting grid, limited by the stock
  in the network.

### Changed
- Tom's Simple Storage and Refined Storage now share the same batching and
  output-handling code. Tom's behavior is unchanged.

### Known limitations
- Only the RS crafting grid is supported (the plain and portable grids have no
  crafting).
- Items with components (enchantments, custom data...) are not counted in the
  RS network stock.

## 0.2.0

### Added
- **Tom's Simple Storage compatibility**: the executor now works from Tom's
  crafting terminal. Ingredients are pulled straight from the storage network
  and crafted items are sent back into it. No hard dependency: the mod loads
  normally without Tom's.
- **Network stock awareness**: while a Tom's terminal is open, the tree counts
  items stored in the network in addition to the player inventory.
- **Ingredient choice for tag ingredients**: recipes that accept several items
  (e.g. `#c:chests`) show a `#N` indicator in the tree. Clicking it opens a
  picker to choose which item to use. The choice is remembered per recipe and
  applied to every matching slot. Without a choice, an item you already own
  is preferred.
- "Reset prefs" also clears ingredient choices.

### Performance
- **Batched crafting**: the executor performs several crafts per click instead
  of one. Long chains (e.g. 63 gearboxes, ~630 crafts) go from several minutes
  to a few seconds.
  - Crafting table / inventory: batch size is limited by free inventory space.
  - Tom's terminal: batch size is limited by the stock in the network.

### Fixed
- The progress bar and the "done" message no longer overlap the Pin / Clear
  pin buttons.
- Tom's: shift-clicking the output no longer crafts far more than requested
  (e.g. 193 gearboxes instead of 64).
- The executor no longer stalls on a step that looked like it crafted nothing
  (in-flight items in the grid were wrongly counted).

## 0.1.0

Initial release.
- Recursive crafting tree opened with `C` on an item: inventory slot, JEI
  ingredient list or JEI bookmarks.
- Recipe choice when an item has several recipes, remembered.
- Automatic client-side craft executor (crafting table and 2x2 inventory grid).
- List of base resources to gather, pinnable to the HUD.
- Quantity = number of items to craft (default: owned + 1).
- Debug tools: Dump, Reset prefs.
