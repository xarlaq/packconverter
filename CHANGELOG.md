# Changelog

## Unreleased

- Copper from iron: old packs without copper tools and armor get them, recoloured from their own iron items (vanilla copper's colour by default, any colour in the settings, with a preview), the way netherite is made from diamond.

## 1.5.1 (Minecraft 1.21.11)

- Old packs' JSON that only Minecraft 1.8 read (a comma after the last entry, comments and the like) is written as JSON the game reads: textures and models of such packs no longer show as missing, animated ender pearls for one.
- Converted packs are converted again once, the first time they're used.

## 1.5.0 (Minecraft 1.21.11)

First version for Minecraft 1.21.11, with everything Pack Converter 1.5.0 has on 26.3:

- Converts resource packs made for Minecraft 1.7 to 1.21.10 when they are applied, cached after the first time.
- Saves converted copies as normal 1.21.11 packs (one pack, or a whole folder), with pack icons and a search.
- Resource pack screen: pack folders, previews of how a pack looks in game, a conversion summary on request, and how many packs each list holds.
- The Pack Converter button is on the title screen and, in a world, in Options next to Resource Packs.
- On Lunar Client, the buttons on the Resource Packs screen are placed right (they ended up in the top left corner).
- Old packs whose pack.mcmeta newer versions reject (escapes like `\!` or line breaks in the description, fine up to 1.8) show up in the pack list and convert.
- Favourite packs (a star on each pack) and a random pack button (from all packs or only favourites), with a key you can set; both can be switched off.
- 1.9 survival and creative inventory layouts with an off-hand slot in the pack's own style.
- Blocks 1.13 merged into one (lit furnace and others) keep working with old packs' blockstates.
- Netherite made from a pack's diamond items, in a colour of your choice (live preview).
- KB sword: diamond swords with Knockback II or more get a colour of their own, the pack's diamond sword recoloured (red by default, any colour in the settings).
- Changing the netherite or KB sword colour, or turning either on or off, now applies right away: no need to press F3+T or reload your packs.
- Fast pack switching: no loading screen and no long freeze; shaders, fonts, sounds and languages kept when unchanged; packs never used before converted in the background first.
- Fixes a bug that dropped wide sprites of HD packs from texture atlases.
- English and Polish.
