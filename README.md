# Riftfall Core

The core mod behind the Riftfall modpack, for NeoForge on Minecraft 1.21.1. It's what makes the pack tick: the rifts and their bosses, the 13 classes, your shattered home and the people who move back into it, plus the data and config that tie the pack's other mods together.

It's built for the pack and isn't meant to be played on its own. If you want to play Riftfall, grab the modpack.

## Credits

A huge amount of credit goes to everyone who was part of the Wanderers of the Rift Discord and modpack project. Many of the dungeon rooms come from that project, along with ideas for some of the mechanics. Every builder is credited in [`ATTRIBUTION.md`](ATTRIBUTION.md) and in the credits screen in game.

- **"Heroes Blade" model:** by Sylvamy, CC BY 4.0.
- **Sky images:** NASA's Scientific Visualization Studio and ESO, CC BY 4.0, credited in game.
- **Everything else:** made for Riftfall by CyberDay1.

## Licence

The code is AGPL-3.0-or-later (see [`LICENSE`](LICENSE)), the same licence as the Wanderers of the Rift pieces it uses. You're free to share and modify it under those terms, including shipping it in a modpack.

The music isn't in this repository: it's licensed separately and only ships inside the release jar. See [`LICENSE-AUDIO.md`](LICENSE-AUDIO.md).

## Building

JDK 21, then:

    ./gradlew build -Priftfall.mavenDeps=true -Priftfall.noAudio=true

The jar lands in `build/libs/`. The optional mod integrations download from Modrinth and CurseMaven, and the build skips the audio a plain checkout doesn't have.

This repository holds one commit per released version, not the full development history.
