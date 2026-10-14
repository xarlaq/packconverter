package dev.packconverter.core.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 1.21.4 moved item model selection out of the models: "overrides" in models/item/X.json were replaced by
 * item definitions in items/X.json, and block items now point straight at block models. Two cases are converted:
 * <ul>
 * <li>overrides that pick the pack's own models (custom model data, damage, custom bows...) become an items/X.json
 * with the same choices. Old overrides were tried last to first, the first whose predicates all matched won;
 * the definition nests the same tests in that order. Vanilla's tints for the item are kept.</li>
 * <li>a pack's own models/item/X.json for an item that now uses a block model directly gets an items/X.json
 * pointing at it, so its customisation (e.g. how a block is held) still applies.</li>
 * </ul>
 * Overrides that only choose vanilla models (bow pulling, compass frames...) need nothing: the vanilla definition
 * picks the same models, which the pack may have replaced.
 */
public final class ItemDefinitions1214Step implements Step {
	private static final int MAX_NODES = 20_000;

	@Override
	public int format() {
		return 46;
	}

	@Override
	public String name() {
		return "1.21.4 item definitions";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		int generated = 0;
		String prefix = "assets/minecraft/models/item/";
		for (String path : tree.paths(prefix)) {
			if (!path.endsWith(".json")) continue;
			String name = path.substring(prefix.length(), path.length() - ".json".length());
			JsonObject model = Json.obj(tree.json(path));
			if (model == null) continue;
			String defPath = "assets/minecraft/items/" + name + ".json";
			if (tree.has(defPath)) continue; // the pack has its own definition
			String vanillaDefPath = "assets/minecraft/" + RenameStep.forward("items/" + name + ".json", "1.21.4");
			JsonObject vanillaDef = Json.obj(ctx.vanilla().json(vanillaDefPath));

			JsonArray overrides = Json.arr(model, "overrides");
			if (overrides != null) {
				model.remove("overrides");
				tree.putJson(path, model);
				// a builtin/entity item (chest, shield, trident...) is drawn by vanilla's special model, which a
				// plain model chain would replace with an empty one
				if (vanillaDef == null || !choosesOwnModels(overrides, ctx) || String.valueOf(Json.str(model, "parent")).endsWith("builtin/entity")) continue;
				JsonElement chain = fromOverrides("minecraft:item/" + name, overrides, baseTints(vanillaDef, name), name, ctx);
				if (chain == null) continue;
				JsonObject def = new JsonObject();
				def.add("model", chain);
				tree.putJson(defPath, def);
				generated++;
			} else if (vanillaDef != null && !ctx.vanilla().exists("assets/minecraft/models/item/" + name + ".json")) {
				// block item: vanilla now draws the block model, the pack customised the item model
				JsonObject root = Json.obj(vanillaDef.get("model"));
				if (root == null || !isType(root, "model")) continue;
				JsonObject def = vanillaDef.deepCopy();
				Json.obj(def, "model").addProperty("model", "minecraft:item/" + name);
				tree.putJson(defPath, def);
				generated++;
			}
		}
		ctx.count("item definitions created", generated);
	}

	/** True if an override picks a model vanilla doesn't have (the pack's own). */
	private static boolean choosesOwnModels(JsonArray overrides, Context ctx) {
		for (JsonElement e : overrides) {
			String model = Json.str(Json.obj(e), "model");
			if (model == null) continue;
			int colon = model.indexOf(':');
			String ns = colon >= 0 ? model.substring(0, colon) : "minecraft";
			if (!ctx.vanilla().exists("assets/" + ns + "/models/" + model.substring(colon + 1) + ".json")) return true;
		}
		return false;
	}

	/** The tints vanilla applies to this item's own model, so dyed/potion-coloured items stay coloured. */
	private static JsonArray baseTints(JsonObject vanillaDef, String name) {
		JsonArray[] found = {null};
		walk(vanillaDef.get("model"), node -> {
			if (found[0] == null && isType(node, "model") && Json.arr(node, "tints") != null) {
				String m = Json.str(node, "model");
				if (m != null && (m.endsWith("item/" + name) || found[0] == null)) found[0] = Json.arr(node, "tints");
			}
		});
		return found[0];
	}

	private static void walk(JsonElement e, java.util.function.Consumer<JsonObject> visit) {
		if (e == null) return;
		if (e.isJsonObject()) {
			visit.accept(e.getAsJsonObject());
			for (Map.Entry<String, JsonElement> c : e.getAsJsonObject().entrySet()) walk(c.getValue(), visit);
		} else if (e.isJsonArray()) {
			for (JsonElement c : e.getAsJsonArray()) walk(c, visit);
		}
	}

	private static boolean isType(JsonObject node, String type) {
		String t = Json.str(node, "type");
		return t != null && (t.equals(type) || t.equals("minecraft:" + type));
	}

	private record OldOverride(Map<String, Double> predicate, String model) {
	}

	static JsonElement fromOverrides(String base, JsonArray overrides, JsonArray tints, String item, Context ctx) {
		List<OldOverride> list = new ArrayList<>();
		for (JsonElement e : overrides) {
			JsonObject o = Json.obj(e);
			String model = Json.str(o, "model");
			JsonObject predicate = Json.obj(o, "predicate");
			if (model == null || predicate == null) continue;
			Map<String, Double> p = new TreeMap<>();
			for (Map.Entry<String, JsonElement> entry : predicate.entrySet()) {
				Double v = Json.num(entry.getValue());
				if (v != null) p.put(entry.getKey(), v);
			}
			list.add(new OldOverride(p, model));
		}
		if (list.isEmpty()) return null;

		// The common case, one numeric predicate throughout (custom model data, damage): a single range_dispatch.
		String only = list.getFirst().predicate().size() == 1 ? list.getFirst().predicate().keySet().iterator().next() : null;
		boolean single = only != null && numeric(only, item) != null;
		for (OldOverride o : list) single &= o.predicate().size() == 1 && o.predicate().containsKey(only);
		if (single) {
			Map<Double, String> byThreshold = new TreeMap<>();
			for (OldOverride o : list) byThreshold.put(o.predicate().get(only), o.model()); // later overrides win ties
			JsonObject node = numeric(only, item);
			JsonArray entries = new JsonArray();
			byThreshold.forEach((t, m) -> {
				JsonObject entry = new JsonObject();
				entry.addProperty("threshold", t);
				entry.add("model", leaf(m, tints));
				entries.add(entry);
			});
			node.add("entries", entries);
			node.add("fallback", leaf(base, tints));
			return node;
		}

		JsonElement result = leaf(base, tints);
		int[] nodes = {1};
		for (OldOverride o : list) {
			JsonElement chained = test(new ArrayList<>(o.predicate().entrySet()), 0, leaf(o.model(), tints), result, item, ctx, nodes);
			if (nodes[0] > MAX_NODES) {
				ctx.warn("items/" + item + ".json: too many override combinations, later ones skipped");
				break;
			}
			result = chained;
		}
		return result;
	}

	/** Tests predicates[i..] in turn; all true -> onTrue, any false -> onFalse. */
	private static JsonElement test(List<Map.Entry<String, Double>> predicates, int i, JsonElement onTrue, JsonElement onFalse,
		String item, Context ctx, int[] nodes) {
		if (i == predicates.size()) return onTrue;
		JsonElement inner = test(predicates, i + 1, onTrue, onFalse, item, ctx, nodes);
		String key = predicates.get(i).getKey();
		double value = predicates.get(i).getValue();
		nodes[0]++;
		JsonObject num = numeric(key, item);
		if (num != null) {
			if (value <= 0 && !key.equals("custom_model_data")) return inner; // ">= 0" always holds
			JsonArray entries = new JsonArray();
			JsonObject entry = new JsonObject();
			entry.addProperty("threshold", value);
			entry.add("model", inner);
			entries.add(entry);
			num.add("entries", entries);
			num.add("fallback", onFalse);
			return num;
		}
		String condition = switch (key) {
			case "pulling", "blocking", "throwing", "tooting" -> "minecraft:using_item";
			case "damaged" -> "minecraft:damaged";
			case "broken" -> "minecraft:broken";
			case "cast" -> "minecraft:fishing_rod/cast";
			default -> null;
		};
		if (condition != null) {
			if (value <= 0) return inner;
			JsonObject node = new JsonObject();
			node.addProperty("type", "minecraft:condition");
			node.addProperty("property", condition);
			node.add("on_true", inner);
			node.add("on_false", onFalse);
			return node;
		}
		if (key.equals("lefthanded") || key.equals("charged") || key.equals("firework")) {
			if (value <= 0) return inner;
			JsonObject node = new JsonObject();
			node.addProperty("type", "minecraft:select");
			node.addProperty("property", key.equals("lefthanded") ? "minecraft:main_hand" : "minecraft:charge_type");
			JsonArray cases = new JsonArray();
			JsonObject c = new JsonObject();
			if (key.equals("lefthanded")) c.addProperty("when", "left");
			else if (key.equals("firework")) c.addProperty("when", "rocket");
			else {
				JsonArray when = new JsonArray();
				when.add("arrow");
				when.add("rocket");
				c.add("when", when);
			}
			c.add("model", inner);
			cases.add(c);
			node.add("cases", cases);
			node.add("fallback", onFalse);
			return node;
		}
		ctx.warn("items/" + item + ".json: predicate \"" + key + "\" has no 1.21.4 equivalent, ignored");
		return inner;
	}

	/** A range_dispatch node (without entries/fallback) for numeric predicates, or null. */
	private static JsonObject numeric(String key, String item) {
		JsonObject node = new JsonObject();
		node.addProperty("type", "minecraft:range_dispatch");
		switch (key) {
			case "custom_model_data" -> node.addProperty("property", "minecraft:custom_model_data");
			case "damage" -> node.addProperty("property", "minecraft:damage");
			case "cooldown" -> node.addProperty("property", "minecraft:cooldown");
			case "filled" -> node.addProperty("property", "minecraft:bundle/fullness");
			case "pull" -> {
				if (item.contains("crossbow")) {
					node.addProperty("property", "minecraft:crossbow/pull");
				} else {
					node.addProperty("property", "minecraft:use_duration");
					node.addProperty("scale", 0.05);
				}
			}
			case "angle" -> {
				node.addProperty("property", "minecraft:compass");
				node.addProperty("target", item.contains("recovery") ? "recovery" : "spawn");
			}
			case "time" -> {
				node.addProperty("property", "minecraft:time");
				node.addProperty("source", "daytime");
			}
			default -> {
				return null;
			}
		}
		return node;
	}

	private static JsonObject leaf(String model, JsonArray tints) {
		JsonObject node = new JsonObject();
		node.addProperty("type", "minecraft:model");
		node.addProperty("model", model.contains(":") ? model : "minecraft:" + model);
		if (tints != null) node.add("tints", tints.deepCopy());
		return node;
	}
}
