package dev.packconverter.mc.folders;

import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.Lunar;

import java.util.List;
import java.util.function.Predicate;

/**
 * Pinned packs: a pin on a pack's row in the Selected list (shown when hovering it, filled once pinned). The random
 * pack leaves pinned packs on and in their place, and puts the pack it picks where the packs it takes off were. Kept
 * by pack id in the settings. Shown with the random pack on, and not on Lunar Client, which draws the rows itself.
 */
public final class Pins {
	private Pins() {
	}

	public static boolean enabled() {
		return !Lunar.detected() && PackConverterConfig.get().randomPack;
	}

	public static boolean is(String packId) {
		return PackConverterConfig.get().pinnedPacks.contains(packId);
	}

	public static void toggle(String packId) {
		PackConverterConfig config = PackConverterConfig.get();
		List<String> pins = config.pinnedPacks;
		if (!pins.remove(packId)) pins.add(packId);
		config.save();
	}

	/**
	 * Where the picked pack goes in a list of packs from the top (highest priority) down, counted in the list without
	 * the packs taken off: where the first of those was, else right under the pinned packs at the top.
	 */
	public static <T> int slot(List<T> topDown, Predicate<T> stays, Predicate<T> pinned) {
		int kept = 0;
		for (T pack : topDown) {
			if (!stays.test(pack)) return kept;
			kept++;
		}
		int under = 0;
		while (under < topDown.size() && pinned.test(topDown.get(under))) under++;
		return under;
	}
}
