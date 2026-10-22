package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

/**
 * JSON that Minecraft 1.8 read but the game now rejects: its Gson was lenient (a comma after the last entry, comments,
 * single quotes, escapes JSON doesn't have), today's reads pack files strictly. A texture's .mcmeta with a comma after
 * its last frame then drops the whole texture: a missing-texture checkerboard in its place. Each JSON file and .mcmeta
 * the pack has as it was is read strictly, the way the game reads it; one that fails is written again from what it
 * meant ({@link Json#parseLoose}). Files a step wrote are strict already; files unreadable either way stay as they are.
 */
public final class StrictJsonStep implements Step {
	@Override
	public String name() {
		return "strict json";
	}

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		int fixed = 0;
		for (String path : tree.paths(p -> (p.endsWith(".json") || p.endsWith(".mcmeta")) && tree.node(p) instanceof PackTree.SourceRef)) {
			byte[] data = tree.bytes(path);
			if (data == null || Json.strict(data)) continue;
			JsonElement json = Json.parseLoose(data);
			if (json == null) continue;
			tree.putJson(path, json);
			fixed++;
		}
		ctx.count("JSON files written strictly", fixed);
	}
}
