package dev.packconverter.core.steps;

import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.io.Json;


/**
 * GUI sprites are drawn as the "gui.scaling" section of their own .mcmeta says (nine-slice, tile or stretch),
 * and that file must come from the same pack as the image. Newer versions nine-slice sprites older ones simply
 * blitted (the inventory effect background since 1.21.11, for one), so a converted sprite without a .mcmeta
 * would be stretched. Sprites with vanilla's proportions get vanilla's scaling section (never its animation).
 */
public final class GuiScalingStep implements Step {
	private static final String SPRITES = "assets/minecraft/textures/gui/sprites/";

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "GUI sprite scaling";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		int added = 0;
		for (String path : tree.paths(SPRITES)) {
			if (!path.endsWith(".png") || tree.has(path + ".mcmeta")) continue;
			JsonObject vanillaMeta = Json.obj(ctx.vanilla().json(path + ".mcmeta"));
			JsonObject gui = Json.obj(vanillaMeta, "gui");
			if (gui == null) continue;
			int[] vanillaSize = Img.pngSize(ctx.vanilla().read(path));
			int[] mine = tree.imageSize(path);
			if (vanillaSize == null || mine == null) continue;
			// same proportions (any resolution): the scaling section describes this image too
			if ((long) mine[0] * vanillaSize[1] != (long) mine[1] * vanillaSize[0]) continue;
			JsonObject meta = new JsonObject();
			meta.add("gui", gui.deepCopy());
			tree.putJson(path + ".mcmeta", meta);
			added++;
		}
		ctx.count("GUI scaling metadata added", added);
	}
}
