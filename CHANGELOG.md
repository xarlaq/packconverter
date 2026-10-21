# Changelog

## 1.5.0 (Minecraft 26.3)

- Netherite and KB sword settings (on or off, colours) show at once in the packs in use: no F3+T, no loading screen.
- KB sword: diamond swords with Knockback II or more get a colour of their own, the pack's diamond sword recoloured (red by default, any colour in the settings). The game picks it from the enchantments, so saved packs do it without the mod too.
- Sounds of packs that stay on while you switch others (a server's pack, for one) no longer go silent until F3+T.
- Old packs whose pack.mcmeta newer versions reject (escapes like `\!` or line breaks in the description, fine up to 1.8) show up in the pack list again, and convert.
- On Lunar Client, the buttons on the Resource Packs screen are placed more reliably.
- The Available and Selected headers show how many packs each list holds, counting every folder.
- Converted packs are converted again once, the first time they're used.

## 1.1.0 (Minecraft 26.3)

- Favourite packs: a star on each pack in the Resource Packs screen. Starred packs stay at the top of the list, from any folder.
- Random pack: a die button next to Done swaps in a random pack, from all packs or only favourites. Right-click it to give it a key (also in Controls).
- Pack switches without the two stutters: the pack list isn't read again as a switch starts, the new packs go in over a few frames instead of one long freeze, and atlases are put together in the background.
- Packs never used before are converted in the background before they're applied (a random pick, or as soon as a pack is moved to Selected), instead of freezing the game as the switch starts.
- Sounds and languages are kept when the packs you switch have none (sounds keep playing), like shaders and fonts already were.
- New settings, all on by default: Hide loading bar, Favourite packs, Random pack (All packs / Favourites / Off).
- Hide loading bar also hides RRLS's (Remove Reloading Screen) bar.
- The reload time in the log is right again.

## 1.0.0 (Minecraft 26.3)

First release.

- Converts resource packs made for Minecraft 1.7 to 26.2 when they are applied, cached after the first time.
- Saves converted copies as normal 26.3 packs (one pack, or a whole folder), with pack icons and a search.
- Resource pack screen: pack folders, previews of how a pack looks in game, a conversion summary on request.
- 1.9 survival and creative inventory layouts with an off-hand slot in the pack's own style.
- Blocks 1.13 merged into one (lit furnace and others) keep working with old packs' blockstates.
- Netherite made from a pack's diamond items, in a colour of your choice (live preview).
- Faster pack switching: no loading screen, shaders and fonts kept when unchanged.
- Fixes a 26.3 bug that dropped wide sprites of HD packs from texture atlases.
- English and Polish.
