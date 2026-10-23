package dev.packconverter.core;

/** Choices that change what a conversion makes, so they are part of the cache key. */
public record Options(boolean netherite, int netheriteColor, boolean knockback, int knockbackColor, boolean copper, int copperColor) {
	/** Vanilla netherite armor's average colour, the default for netherite made from diamond. */
	public static final int NETHERITE = 0x443940;
	/** Red, the default for diamond swords with Knockback II. */
	public static final int KNOCKBACK = 0xD62828;
	/** Vanilla copper armor's average colour, the default for copper made from iron. */
	public static final int COPPER = 0xC7694A;
	/** What the game uses unless the settings say otherwise. */
	public static final Options DEFAULT = new Options(true, NETHERITE, true, KNOCKBACK, true, COPPER);
	/** Only what the pack has (the tests compare conversions with vanilla). */
	public static final Options PLAIN = new Options(false, NETHERITE, false, KNOCKBACK, false, COPPER);

	public Options {
		netheriteColor &= 0xFFFFFF;
		knockbackColor &= 0xFFFFFF;
		copperColor &= 0xFFFFFF;
	}

	/** These options with netherite on or off, in this colour. */
	public Options withNetherite(boolean on, int color) {
		return new Options(on, color, this.knockback, this.knockbackColor, this.copper, this.copperColor);
	}

	/** Part of the cache key; empty for {@link #PLAIN}, so plain conversions keep the key they always had. */
	public String key() {
		return (this.netherite ? "|netherite:" + String.format("%06x", this.netheriteColor) : "")
			+ (this.knockback ? "|knockback:" + String.format("%06x", this.knockbackColor) : "")
			+ (this.copper ? "|copper:" + String.format("%06x", this.copperColor) : "");
	}
}
