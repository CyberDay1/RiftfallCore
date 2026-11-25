# Stellar workshop GUI textures

`sun.png` and `script.png` are gold alpha-mask exports of the existing astronomical
SVG masters in `art/holy_relics/astronomical/vectors/`. They reuse the World
Rebuilder/Shattered World Shard inscriptions without changing those assets.
The Sun seal is 64×64. The Sun–Earth–Moon strip is 128×40, cropped from the
center of a square render so the symbols retain their proportions.

Regenerate from the repository root with `node tools/models/build_workshop_runes.cjs`.
The script uses the local bundled Playwright runtime and Chrome to rasterize the
existing vectors; it does not fetch assets. `StellarWorkshop` paints the remaining
native GUI surfaces and preserves Minecraft's 16px item contents/18px slot pitch.
