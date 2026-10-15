package dev.packconverter.mc.folders;

import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.Lunar;

import java.util.List;

/**
 * Favourite packs: a star on a pack's row in the resource pack screen (shown when hovering it, filled once starred).
 * Favourites come first in the Available list, and at its top level from every folder too. Kept by pack id in the
 * settings. Not on Lunar Client, which draws the pack rows itself.
 */
public final class Favourites {
	private Favourites() {
	}

	public static boolean enabled() {
		return !Lunar.detected();
	}

	public static boolean is(String packId) {
		return PackConverterConfig.get().favouritePacks.contains(packId);
	}

	public static void toggle(String packId) {
		PackConverterConfig config = PackConverterConfig.get();
		List<String> favourites = config.favouritePacks;
		if (!favourites.remove(packId)) favourites.add(packId);
		config.save();
	}
}
