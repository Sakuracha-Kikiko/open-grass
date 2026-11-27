# Open Grass Changelog

## 1.5.2.3

- Added the `glow_lichen` layer type: glow lichen (the `down=true` state) is
  drawn on blocks next to it, like snow and sculk.
- `match` now accepts block state selectors with properties, e.g.
  `minecraft:glow_lichen[down=true]`.
- Data coverage: 43 new `states` and 24 new `data` files (candle cakes,
  copper doors and trapdoors including the waxed variants, mud/polished
  tuff/tuff/tuff brick walls, `chorus_flower`, `chorus_plant`,
  `dragon_egg`, bamboo and cherry buttons, `sculk_vein`); 19 empty `data`
  files were removed. 319 states and 212 data files now ship.
- Fixed transparent layers drawn as solid black: the layer is emitted in the
  host block's render type, so hosts that rendered as `solid` (walls,
  stairs, ...) turned the transparent pixels of the lichen texture black.
  They are now rendered on the layer's declared render type (`cutout`), and
  models that split their quads per render type (multipart walls) no longer
  lose geometry.

## 1.5.2.2

- Layer types can now render a vanilla block state instead of a custom model.
  - Added the `display` key: the block state drawn by the layer, e.g.
    `minecraft:sculk_vein[down=true]`.
  - Added the `match` key: which blocks count as this layer (block ids,
    `#tags`, or a list of both); it defaults to the `block` itself.
  - `snow`, `moss` and `sculk` now draw vanilla states; `snowy_layer`,
    `mossy_layer` and `sculky_layer` were removed.
  - Layers follow resource packs and are culled like the vanilla block.
  - The old `model` key still works, `ash` still uses it.
- Documented the layer type format in the porting notes.

## 1.5.2.1

Initial port of LambdaBetterGrass 1.5.2 (MIT) to Minecraft 1.21.1 /
NeoForge 21.1.x, without mixins and without an access widener.

- Better grass: `OFF`, `FASTEST`, `FAST` and `FANCY` modes, including the
  `blend_up` and `arch` connection shapes.
- Better snow: snow, moss and sculk layers on non-full blocks, and snowy
  fences (built in, no resource pack to enable).
- The 1.5.2 JSON data is kept: 207 `data` and 276 `states` files, renamed
  for 1.21.1 (`grass` → `short_grass`); 272 states load, the four unknown
  blocks (`cave_vines_head`, `skeleton_head`, `weeping_wines`,
  `wither_skeleton_head`) are skipped as upstream does.
- Settings screen with mode, better snow and reset, reachable from the mod
  list; stored in `config/open_grass-client.toml`.
- Fixed `blend_up_m` connection textures being mirrored: the dirt part of
  the side texture flipped while only the grass shape should mirror.
- Removed the Quilt migration notice and the `Main` mixin that refused to
  start on Fabric.
