package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;

/**
 * Copper (1.21.9) for packs that have iron items but no copper ones (a setting, on by default), the way
 * {@link NetheriteStep} makes netherite from diamond: the pack's own iron tools, armor items and worn armor
 * recoloured to copper, or to a colour of the player's choosing. Iron is grey, so its grey pixels change
 * ({@link NetheriteStep#recolorGrey}; a wooden handle, the only colour on an iron sword, stays), each keeping its
 * shading. A pack's own iron item model is copied for the copper one when it uses the iron texture.
 */
public final class CopperStep implements Step {
	private static final String ITEM = "assets/minecraft/textures/item/";
	private static final String EQUIPMENT = "assets/minecraft/textures/entity/equipment/";

	@Override
	public String name() {
		return "copper from iron";
	}

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		if (!ctx.options().copper()) return;
		int color = ctx.options().copperColor();
		int made = 0;
		for (String item : NetheriteStep.ITEMS) {
			if (NetheriteStep.make(tree, ITEM + "iron_" + item + ".png", ITEM + "copper_" + item + ".png", img -> NetheriteStep.recolorGrey(img, color))) {
				made++;
				NetheriteStep.copyModel(tree, "iron_" + item, "copper_" + item);
			}
		}
		for (String layer : NetheriteStep.ARMOR) {
			if (NetheriteStep.make(tree, EQUIPMENT + layer + "/iron.png", EQUIPMENT + layer + "/copper.png", img -> NetheriteStep.recolorGrey(img, color))) made++;
		}
		ctx.count("copper textures made from iron", made);
	}
}
