package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Last line of defence against missing-model boxes: a pack file that replaces a vanilla file must still fit the
 * target version. Blockstates may only use properties the block still has, and every vanilla variant must stay
 * covered (uncovered ones are taken from vanilla). Models whose parent chain is broken are dropped so the
 * vanilla model applies (the pack's textures still do).
 */
public final class ValidateStep implements Step {
	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "validate";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		blockstates(tree, ctx);
		models(tree, ctx);
	}

	private static void blockstates(PackTree tree, Context ctx) {
		String prefix = "assets/minecraft/blockstates/";
		int dropped = 0, merged = 0;
		for (String path : tree.paths(prefix)) {
			if (!path.endsWith(".json")) continue;
			JsonObject vanilla = Json.obj(ctx.vanilla().json(path));
			JsonObject pack = Json.obj(tree.json(path));
			if (vanilla == null || pack == null) {
				// no such block any more (or unreadable): the game would ignore or choke on it
				tree.remove(path);
				dropped++;
				continue;
			}
			Map<String, Set<String>> properties = properties(vanilla);
			if (!usesOnly(pack, properties)) {
				ctx.warn("Dropped " + path + ": uses block properties that no longer exist");
				tree.remove(path);
				dropped++;
				continue;
			}
			JsonObject packVariants = Json.obj(pack, "variants");
			JsonObject vanillaVariants = Json.obj(vanilla, "variants");
			if (packVariants != null && vanillaVariants != null) {
				boolean changed = false;
				for (Map.Entry<String, JsonElement> v : vanillaVariants.entrySet()) {
					Map<String, String> state = parse(v.getKey());
					boolean covered = false;
					for (String key : packVariants.keySet()) {
						if (matches(parse(key), state)) {
							covered = true;
							break;
						}
					}
					if (!covered) {
						packVariants.add(v.getKey(), v.getValue().deepCopy());
						changed = true;
					}
				}
				if (changed) {
					tree.putJson(path, pack);
					merged++;
				}
			} else if (packVariants != null || Json.arr(pack, "multipart") == null) {
				if (vanillaVariants == null && packVariants != null) {
					// vanilla moved to multipart: a variants file can't cover it reliably
					ctx.warn("Dropped " + path + ": block now uses a multipart definition");
					tree.remove(path);
					dropped++;
				}
			}
		}
		ctx.count("blockstates dropped (incompatible)", dropped);
		ctx.count("blockstates completed from vanilla", merged);
	}

	/** Property name -> values, from variant keys and multipart conditions. */
	private static Map<String, Set<String>> properties(JsonObject blockstate) {
		Map<String, Set<String>> props = new HashMap<>();
		JsonObject variants = Json.obj(blockstate, "variants");
		if (variants != null) {
			for (String key : variants.keySet()) parse(key).forEach((k, v) -> props.computeIfAbsent(k, x -> new HashSet<>()).add(v));
		}
		var multipart = Json.arr(blockstate, "multipart");
		if (multipart != null) {
			for (JsonElement part : multipart) collectWhen(Json.obj(Json.obj(part), "when"), props);
		}
		return props;
	}

	private static void collectWhen(JsonObject when, Map<String, Set<String>> props) {
		if (when == null) return;
		for (Map.Entry<String, JsonElement> e : when.entrySet()) {
			if (e.getKey().equals("OR") || e.getKey().equals("AND")) {
				var list = Json.arr(e.getValue());
				if (list != null) for (JsonElement c : list) collectWhen(Json.obj(c), props);
				continue;
			}
			String values = e.getValue().isJsonPrimitive() ? e.getValue().getAsString() : "";
			for (String v : values.split("\\|")) props.computeIfAbsent(e.getKey(), x -> new HashSet<>()).add(v.startsWith("!") ? v.substring(1) : v);
		}
	}

	private static boolean usesOnly(JsonObject pack, Map<String, Set<String>> properties) {
		JsonObject variants = Json.obj(pack, "variants");
		if (variants != null) {
			for (String key : variants.keySet()) {
				for (Map.Entry<String, String> p : parse(key).entrySet()) {
					Set<String> values = properties.get(p.getKey());
					if (values == null || !values.contains(p.getValue())) return false;
				}
			}
		}
		var multipart = Json.arr(pack, "multipart");
		if (multipart != null) {
			Map<String, Set<String>> used = new HashMap<>();
			for (JsonElement part : multipart) collectWhen(Json.obj(Json.obj(part), "when"), used);
			for (Map.Entry<String, Set<String>> u : used.entrySet()) {
				Set<String> values = properties.get(u.getKey());
				if (values == null) return false;
				for (String v : u.getValue()) if (!values.contains(v)) return false;
			}
		}
		return true;
	}

	/** "facing=north,half=top" -> {facing: north, half: top}; "" -> {}. */
	private static Map<String, String> parse(String key) {
		Map<String, String> map = new HashMap<>();
		if (key.isEmpty()) return map;
		for (String part : key.split(",")) {
			int eq = part.indexOf('=');
			if (eq > 0) map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
		}
		return map;
	}

	private static boolean matches(Map<String, String> condition, Map<String, String> state) {
		for (Map.Entry<String, String> c : condition.entrySet()) {
			if (!c.getValue().equals(state.get(c.getKey()))) return false;
		}
		return true;
	}

	private static final Set<String> BUILTIN = Set.of("builtin/generated", "minecraft:builtin/generated");

	private static void models(PackTree tree, Context ctx) {
		int dropped = 0;
		for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/models/") && p.endsWith(".json"))) {
			String broken = brokenParent(tree, ctx, path, new HashSet<>());
			if (broken != null) {
				tree.remove(path);
				dropped++;
				if (dropped <= 20) ctx.warn("Dropped " + path + ": parent " + broken + " doesn't exist any more");
			}
		}
		ctx.count("models dropped (missing parent)", dropped);
	}

	/** The first missing model in the parent chain, or null if it resolves (in the pack or in vanilla). */
	private static String brokenParent(PackTree tree, Context ctx, String path, Set<String> seen) {
		if (!seen.add(path) || seen.size() > 32) return null;
		JsonObject model = Json.obj(tree.json(path));
		if (model == null) return null;
		String parent = Json.str(model, "parent");
		if (parent == null || BUILTIN.contains(parent)) return null;
		int colon = parent.indexOf(':');
		String ns = colon >= 0 ? parent.substring(0, colon) : "minecraft";
		String parentPath = "assets/" + ns + "/models/" + parent.substring(colon + 1) + ".json";
		if (tree.has(parentPath)) return brokenParent(tree, ctx, parentPath, seen);
		if (ctx.vanilla().exists(parentPath)) return null;
		return parent;
	}
}
