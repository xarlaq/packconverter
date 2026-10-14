package dev.packconverter.mc;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Lunar Client runs Fabric mods on its own loader fork and puts its own pack manager (with folders) on the pack
 * screen. Its parts show up as Fabric mods ("lunar", older builds "ichor"); its launcher class keeps a readable
 * name. Any of them means Lunar.
 */
public final class Lunar {
	private static final boolean DETECTED = detect();

	private Lunar() {
	}

	public static boolean detected() {
		return DETECTED;
	}

	private static boolean detect() {
		if (Boolean.getBoolean("packconverter.pretendLunar")) return true; // dev: the Lunar paths in a normal game (-PpretendLunar)
		FabricLoader loader = FabricLoader.getInstance();
		if (loader.isModLoaded("lunar") || loader.isModLoaded("ichor")) return true;
		try {
			Class.forName("com.moonsworth.lunar.genesis.Genesis", false, Lunar.class.getClassLoader());
			return true;
		} catch (Throwable e) {
			return false;
		}
	}
}
