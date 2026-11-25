# Texture work outstanding — processor placeholder blocks

The fifteen `processor_block_<n>.png` files in `assets/riftfall/textures/block/` are **generated
stand-ins, not art**. The rest of that folder (barrel tiers, key forge, rift chest, portal and tear,
World Breaker, holy core) is real art and is not covered here. The stand-ins were produced by `tools/gen_placeholder_textures.py` (16x16, flat hue + coarse checker + the
slot number in a 3x5 bitmap font). They are correct, legible and ugly, and they exist so the models
reference paths that actually resolve rather than leaving a build full of missing-texture
checkerboards.

## The full list of paths that need real art

Fifteen files, one per material slot. Every one of the twelve shapes in a family (cube, slab,
stairs, wall, fence, fence gate, button, pressure plate, trapdoor, glass, glass pane, directional
pillar) samples the same single texture, so replacing one file re-skins all twelve at once.

| Path | Slot semantics |
| --- | --- |
| `assets/riftfall/textures/block/processor_block_1.png` | 1 — Wall |
| `assets/riftfall/textures/block/processor_block_2.png` | 2 — Path |
| `assets/riftfall/textures/block/processor_block_3.png` | 3 — Floor |
| `assets/riftfall/textures/block/processor_block_4.png` | 4 — Alt Wall |
| `assets/riftfall/textures/block/processor_block_5.png` | 5 — Alt Floor |
| `assets/riftfall/textures/block/processor_block_6.png` | 6 — Planks |
| `assets/riftfall/textures/block/processor_block_7.png` | 7 — Bricks |
| `assets/riftfall/textures/block/processor_block_8.png` | 8 — Alt Path |
| `assets/riftfall/textures/block/processor_block_9.png` | 9 — generic accent |
| `assets/riftfall/textures/block/processor_block_10.png` | 10 — generic accent |
| `assets/riftfall/textures/block/processor_block_11.png` | 11 — generic accent |
| `assets/riftfall/textures/block/processor_block_12.png` | 12 — generic accent |
| `assets/riftfall/textures/block/processor_block_13.png` | 13 — generic accent |
| `assets/riftfall/textures/block/processor_block_14.png` | 14 — generic accent |
| `assets/riftfall/textures/block/processor_block_15.png` | 15 — generic accent |

## What each one should look like

These are build-time tools, not blocks a player ever sees. Legibility beats beauty on every axis.

- **16x16, no animation, no `.mcmeta`.** The pane and glass models are on the `cutout` render
  layer, so alpha is available but nothing currently uses it.
- **A distinct hue per slot, unmistakable at a glance and across a whole room.** Slot 1 and slot 6
  must not be confusable when a builder is stood in a half-finished corridor. The generator
  already spaces hues by 7/15 of the wheel for this reason; keep at least that separation.
- **The slot number legible on every face.** This is the single most important property. A builder
  looking at a wall needs to read "that's 4, I wanted 1" without opening F3 or hovering the item.
  Keep the digits high-contrast against the fill and away from the outer 1px so they survive being
  cut down into a stair, slab or button.
- **A visible edge.** A 1px darker border makes block boundaries readable in a solid field of one
  material, which is exactly what a placeholder room is.
- **Flat, matte, low detail.** Anything with real material character (grain, mortar lines,
  weathering) actively hurts: it suggests the room will look like that, and it will not — the
  theme processor swaps every one of these out at generation time.
- Slots **1-8 carry meaning** (see the table); it is worth encoding that in the art too, e.g. a
  faint icon in a corner. Slots **9-15 are unassigned accents** and should read as neutral, so a
  theme is free to map them to anything.

## Regenerating the stand-ins

```
python tools/gen_placeholder_textures.py
```

Overwrites all fifteen. Delete the script (and this section) once real art lands.
