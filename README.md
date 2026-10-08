![Minecraft Version](https://img.shields.io/badge/Minecraft-26.2-blue?style=for-the-badge&logo=minecraft)
![Fabric](https://img.shields.io/badge/Fabric-Supported-00B2FF?style=for-the-badge&logo=fabric)
![License](https://img.shields.io/badge/License-MPL2.0-green?style=for-the-badge)

![Title card](https://cdn.modrinth.com/data/cached_images/3efa91050263ad9460a57adb4e46afa7ac589c83.jpeg)

## What is AnaTerra?

AnaTerra (*Analytical Terrain*) is based on the research and GLSL-shader by [Runevision](https://blog.runevision.com/2026/03/fast-and-gorgeous-erosion-filter.html). It aims to generate erosion- and geological features without any complex simulations, while refining it with different additional algorithms to create realistic and unique landforms.


A similar mod has been created recently using this technique, see [CATTO by oovaa8](https://modrinth.com/mod/catto), but the scope of AnaTerra goes beyond its own terrain generation.

> I only stumbled upon their implementation after I started my own. Feel free to check out their mod too.


Most terrain mods force a frustrating choice: either adopt a single locked Terrain mod OR rely on vanilla chunk scaling. Most biome mods create beautiful flora, but lock themselves to default world curves.

**AnaTerra** breaks this limitation by acting as a layer between raw terrain heightmap math and third-party feature generation.

* **Standalone:** AnaTerra calculates smooth, mathematically precise real-scale continents, mountain ranges, and river basins.
* **With Other Mods:** Instead of replacing external biome mods, AnaTerra tells them *how and where* to place their features on top of its real-scale computed terrain.


Status of development
---
AnaTerra is in a very early stage of development.
Extensive planning and feature-revision may happen over the course of development.

The initial focus will be on its practical implementation.


## Key Features

### 1. Real-Scale Analytical Heightmaps
AnaTerra generates sweeping, continuous landscape contours using advanced mathematical noise functions and erosion approximation.

### 2. 3rd-party Terrain mod support
AnaTerra functions as a base layer, external terrain mods can be loaded alongside it.
Any mod that uses the vanilla biome distribution, should work out-of-the-box.

### 3. Dynamic Modded Biome Adaptability
When paired with mods like *Terralith* and *Biomes O' Plenty*, AnaTerra doesn't disable or overwrite them. It **integrates** them. Trees, plants, and structures from your favorite mods spawn seamlessly according to AnaTerra's terrain rules.

## Compatibility (WIP)

AnaTerra aims to support other biome and worldgen mods natively, to utilize their individual strengths and biome types.
A few of the supported mods will be listed here, once extensive testing has been done.

The plan is to implement two different modes which dictates how Mods / Datapacks like Terralith impact AnaTerra's world generation.

|Mode|change|
|:-- |:-- |
|True Terrain | e.g. Terralith will map their terrain features on AnaTerra terrain|
|Terrain instances | e.g. Terralith will keep its own terrain generation style for certain biomes, in addition to AnaTerra's own terrain around it|

## Recommended Mods

- Due to the large scale nature of AnaTerra, some optimization mods of your choice can help to improve performance.

- Furthermore, must-haves to fully enjoy the view are either [Voxy](https://modrinth.com/mod/voxy), [Distant Horizons](https://modrinth.com/mod/distanthorizons) or [Bobby](https://modrinth.com/mod/bobby)

## Frequently Asked Questions

> **_Do I need other biome mods for AnaTerra to work?_**
> No! AnaTerra works completely standalone with its own mathematical terrain and biome distribution.

> **_Does AnaTerra replace vanilla biomes?_**
> AnaTerra reshapes the world heightmap and reorganizes biome placement according to its slope and moisture matrix, while preserving vanilla structures and biomes.

Special thanks and attributions to..
---
> ..Rune Skovbo Johansen (Runevision), building on earlier work by Clay John and Felix Westin (Fewes).

> ..oovaa8 for their prior implementation of this technique, and the only other person I've seen to use it.

AI disclosure
---
This project uses AI assisted coding for faster iteration and implementation.
Quality and function are the top priority. 

I value honesty and transparency, especially with a topic that might be sensitive for many people. I respect Modrinth and its userbase, so it is only fair to talk about this.

### What is *NOT* AI..
From the first concepts and ideas to the Graphics, mod pages, etc. are completely untouched by AI and 100% human made.
I take quality checks seriously and I am looking forward to what the future holds for this mod.

> I want to make mods and addons with heart and love, no one wants to play a half-broken mod that has no soul.

<details>
<summary>footnote</summary>
As a technical artist, I fully understand the critics, but at the same time I am intrigued by new technology. I want to test its capabilities and at the end of the day, realize well thought-through projects to have some fun with as a player myself.
  
My rule of thumb for creators is in general (not limited to AI):
- "Would you be satisfied with the result, to play the mod yourself?"
- No?- then no one else will play it.
</details>

## License & Links

* **License:** MPL-2.0 License
* **Reference:** [Runevision: Fast and gorgeous erosion](https://blog.runevision.com/2026/03/fast-and-gorgeous-erosion-filter.html) under MPL-2.0 license
