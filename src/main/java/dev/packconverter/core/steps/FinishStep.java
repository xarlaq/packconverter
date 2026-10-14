package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.Converter;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

/**
 * Last step for every converted pack: removes what can't be converted (shader programs and post effects,
 * rewritten for every new rendering backend), and writes a pack.mcmeta declaring the current format, keeping
 * the description and every other section the pack had.
 */
public final class FinishStep implements Step {
	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "finish";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		int dropped = 0;
		for (String path : tree.paths(p -> p.startsWith("assets/") && (p.contains("/shaders/") || p.contains("/post_effect/")))) {
			tree.remove(path);
			dropped++;
		}
		if (dropped > 0) ctx.warn("Removed " + dropped + " shader files (old shaders don't compile on " + Converter.TARGET + ")");

		JsonObject root = Json.obj(tree.json("pack.mcmeta"));
		if (root == null) root = new JsonObject();
		JsonObject pack = Json.obj(root, "pack");
		JsonObject out = new JsonObject();
		JsonElement description = pack != null ? pack.get("description") : null;
		out.add("description", description != null ? description : new com.google.gson.JsonPrimitive(""));
		out.addProperty("min_format", Converter.TARGET.major());
		out.addProperty("max_format", Converter.TARGET.major());
		root.add("pack", out);
		root.remove("overlays");
		tree.putJson("pack.mcmeta", root);
	}
}
