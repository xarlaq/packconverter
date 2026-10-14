package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;

import java.util.regex.Pattern;

/**
 * Decoding PNGs is most of a conversion's time, and most steps read just one or two sheets in turn. This decodes
 * every image a step may rework (old and new names) at once on all cores; renames carry decoded images along.
 */
public final class PrefetchStep implements Step {
	private static final Pattern SHEETS = Pattern.compile("assets/minecraft/textures/("
		+ "gui/[^/]+\\.png|gui/container/.+\\.png|gui/title/.+\\.png|gui/advancements/.+\\.png|gui/realms/.+\\.png"
		+ "|items?/(compass|clock)\\.png|blocks?/water_(still|flow|overlay)\\.png|blocks?/bed_.+\\.png"
		+ "|misc/enchanted_item_glint\\.png|environment/moon_phases\\.png|map/map_icons\\.png"
		+ "|particle/particles\\.png|painting/paintings_kristoffer_zetterstrand\\.png|entity/(explosion|sweep)\\.png"
		+ "|entity/chest/.+\\.png|entity/bed/.+\\.png|entity/signs/.+\\.png"
		+ "|entity/(cow|pig|horse|slime|sheep|conduit|projectiles)/.+\\.png)");

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "decode sheets";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		tree.prefetchImages(tree.paths(p -> SHEETS.matcher(p).matches()));
	}
}
