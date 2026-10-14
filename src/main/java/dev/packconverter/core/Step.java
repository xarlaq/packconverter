package dev.packconverter.core;

/**
 * One historical change to the resource pack format, undone for packs made before it.
 * Steps run in version order; a step runs when the pack's declared format is older than {@link #format()}.
 * Changes Mojang made inside one format number (1.14 kept 1.13's format 4) use the next number and check
 * for the old files instead.
 */
public interface Step {
	/** First pack format that has this change. */
	int format();

	/** Short name for logs, e.g. "1.20.2 GUI sprites". */
	String name();

	void apply(PackTree tree, Context ctx);
}
