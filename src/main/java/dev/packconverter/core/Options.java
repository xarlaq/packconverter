package dev.packconverter.core;

/** Choices that change what a conversion makes, so they are part of the cache key. */
public record Options(boolean netherite, int netheriteColor) {
	/** Vanilla netherite armor's average colour, the default for netherite made from diamond. */
	public static final int NETHERITE = 0x443940;
	/** What the game uses unless the settings say otherwise. */
	public static final Options DEFAULT = new Options(true, NETHERITE);
	/** Only what the pack has (the tests compare conversions with vanilla). */
	public static final Options PLAIN = new Options(false, NETHERITE);

	public Options {
		netheriteColor &= 0xFFFFFF;
	}

	/** Part of the cache key; empty for {@link #PLAIN}, so plain conversions keep the key they always had. */
	public String key() {
		return this.netherite ? "|netherite:" + String.format("%06x", this.netheriteColor) : "";
	}
}
