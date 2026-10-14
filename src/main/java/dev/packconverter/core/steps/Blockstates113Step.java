package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Before 1.13, blockstate files named models relative to models/block/ ("model": "stone") and the
 * stateless variant was called "normal". 1.13 made model ids full paths ("block/stone") and renamed "normal"
 * to "". Runs before the 1.13 renames so their model-id map applies to full paths. Pairs of blocks that 1.13 made one
 * block with a property are merged ({@link #merge}).
 */
public final class Blockstates113Step implements Step {
	@Override
	public int format() {
		return 4;
	}

	@Override
	public String name() {
		return "1.13 blockstate format";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		int changed = 0;
		for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/blockstates/") && p.endsWith(".json"))) {
			JsonObject root = Json.obj(tree.json(path));
			if (root == null) continue;
			boolean dirty = false;
			JsonObject variants = Json.obj(root, "variants");
			if (variants != null) {
				if (variants.has("normal") && !variants.has("")) {
					variants.add("", variants.remove("normal"));
					dirty = true;
				}
				dirty |= prefixModels(variants);
			}
			if (root.has("multipart")) dirty |= prefixModels(root.get("multipart"));
			if (dirty) {
				tree.putJson(path, root);
				changed++;
			}
		}
		ctx.count("blockstates updated", changed);
		int merged = merge(tree, ctx, "furnace", "lit_furnace", "furnace", "lit")
			+ merge(tree, ctx, "redstone_lamp", "lit_redstone_lamp", "redstone_lamp", "lit")
			+ merge(tree, ctx, "unpowered_repeater", "powered_repeater", "repeater", "powered");
		ctx.count("blocks merged into one (1.13)", merged);
	}

	/**
	 * 1.13 made some pairs of blocks one block with a property (lit_furnace became furnace[lit=true]). A pack's
	 * blockstates for the pair become the one block's: its own variants for each half, vanilla's for a half it has no
	 * file for (so a pack with only furnace.json still shows a lit furnace lit, with its own textures). Multipart
	 * files are left as they are.
	 */
	private static int merge(PackTree tree, Context ctx, String off, String on, String target, String property) {
		String dir = "assets/minecraft/blockstates/";
		String offPath = dir + off + ".json", onPath = dir + on + ".json", targetPath = dir + target + ".json";
		if (!tree.has(offPath) && !tree.has(onPath)) return 0;
		JsonObject offVariants = Json.obj(Json.obj(tree.json(offPath)), "variants"), onVariants = Json.obj(Json.obj(tree.json(onPath)), "variants");
		if (tree.has(offPath) && offVariants == null || tree.has(onPath) && onVariants == null) return 0;
		JsonObject vanilla = Json.obj(Json.obj(ctx.vanilla().json(targetPath)), "variants");
		if (vanilla == null) return 0;
		JsonObject variants = new JsonObject();
		addHalf(variants, offVariants, property + "=false", vanilla);
		addHalf(variants, onVariants, property + "=true", vanilla);
		JsonObject root = new JsonObject();
		root.add("variants", variants);
		tree.remove(offPath);
		tree.remove(onPath);
		tree.putJson(targetPath, root);
		return 1;
	}

	/** The pack's variants with the state added to each key, or vanilla's variants in that state. */
	private static void addHalf(JsonObject into, JsonObject own, String state, JsonObject vanilla) {
		if (own != null) {
			for (Map.Entry<String, JsonElement> e : own.entrySet()) into.add(e.getKey().isEmpty() ? state : e.getKey() + "," + state, e.getValue());
			return;
		}
		for (Map.Entry<String, JsonElement> e : vanilla.entrySet()) {
			if (List.of(e.getKey().split(",")).contains(state)) into.add(e.getKey(), e.getValue().deepCopy());
		}
	}

	/**
	 * "model": "x" -> "block/x". Before 1.13 the game prepended "block/" to every blockstate model, folders
	 * included; ids already starting with "block/" are left alone (packs mislabeled as old).
	 */
	private static boolean prefixModels(JsonElement json) {
		boolean dirty = false;
		if (json.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
				String value = Json.str(e.getValue());
				if (value != null && e.getKey().equals("model")) {
					int colon = value.indexOf(':');
					String path = value.substring(colon + 1);
					if (!path.startsWith("block/")) {
						e.setValue(new JsonPrimitive(value.substring(0, colon + 1) + "block/" + path));
						dirty = true;
					}
				} else if (!e.getValue().isJsonPrimitive()) {
					dirty |= prefixModels(e.getValue());
				}
			}
		} else if (json.isJsonArray()) {
			List<JsonElement> items = new ArrayList<>();
			json.getAsJsonArray().forEach(items::add);
			for (JsonElement e : items) dirty |= prefixModels(e);
		}
		return dirty;
	}
}
