# BitChord glass adaptation

Source: [BitChord LiquidGlass.kt](https://github.com/kushagrasinghx/BitChord/blob/04a4a83889bb8fa5bd6ee6f6097f0c6608671d68/app/src/main/java/com/music/bitchord/ui/components/LiquidGlass.kt),
commit `04a4a83889bb8fa5bd6ee6f6097f0c6608671d68`, GPL-3.0.

The lightweight glass and rim-only approach is adapted for Sonora's player:
a directional brush border on the artwork and transport panel adds a glass
highlight without another blur or continuously animated capture. Sonora uses
Compose brush borders and its existing Haze glass renderer, rather than copying
BitChord's vendored renderer. BitChord's source credits Echo-Music (GPL-3.0)
and Kyant0/backdrop (Apache-2.0).

This is an adaptation, not a Git cherry-pick across the incompatible app trees.
The transport backdrop records the player's own background separately from its
controls. The existing disable-blur preference bypasses that transport effect.
