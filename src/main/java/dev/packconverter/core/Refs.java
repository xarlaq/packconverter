package dev.packconverter.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.packconverter.core.io.Json;

import java.util.Map;

/**
 * Rewrites resource references inside a pack's JSON files after files were renamed: model parents and
 * textures, blockstate and item-definition models, font bitmaps, particle sprites, atlas single sprites and
 * sounds.json entries. References keep their original style (with or without "minecraft:").
 */
public final class Refs {
	public enum Kind {TEXTURE, MODEL, SOUND}

	/** Returns the new path for a reference, or null to keep it. Paths are ids without namespace. */
	@FunctionalInterface
	public interface Mapper {
		String map(Kind kind, String namespace, String path);
	}

	private Refs() {
	}

	/** Mapper over plain id maps (minecraft namespace only), as built from a rename table step. */
	public static Mapper of(Map<String, String> textures, Map<String, String> models, Map<String, String> sounds) {
		return (kind, ns, path) -> {
			if (!ns.equals("minecraft")) return null;
			return switch (kind) {
				case TEXTURE -> textures.get(path);
				case MODEL -> models.get(path);
				case SOUND -> sounds.get(path);
			};
		};
	}

	/** Rewrites every JSON reference in the tree; returns the number of files changed. */
	public static int rewrite(PackTree tree, Mapper mapper) {
		int changed = 0;
		for (String path : tree.paths("assets/")) {
			if (!path.endsWith(".json")) continue;
			String[] parts = path.split("/", 4);
			if (parts.length < 4) continue;
			String ns = parts[1], rest = parts[2] + "/" + parts[3];
			Fixer fixer;
			if (rest.startsWith("models/")) fixer = Refs::model;
			else if (rest.startsWith("blockstates/") || rest.startsWith("items/")) fixer = Refs::modelRefsAnywhere;
			else if (rest.startsWith("font/")) fixer = Refs::font;
			else if (rest.startsWith("particles/")) fixer = Refs::particle;
			else if (rest.startsWith("atlases/")) fixer = Refs::atlas;
			else if (rest.equals("sounds.json")) fixer = Refs::sounds;
			else continue;
			JsonElement json = tree.json(path);
			if (json == null) continue;
			if (fixer.fix(json, ns, mapper)) {
				tree.putJson(path, json);
				changed++;
			}
		}
		return changed;
	}

	@FunctionalInterface
	private interface Fixer {
		boolean fix(JsonElement json, String ns, Mapper mapper);
	}

	/** Maps "ns:path" or "path" (resolved against {@code defaultNs}); null if unchanged. */
	static String mapId(String id, Kind kind, String defaultNs, Mapper mapper) {
		int colon = id.indexOf(':');
		String ns = colon >= 0 ? id.substring(0, colon) : defaultNs;
		String path = colon >= 0 ? id.substring(colon + 1) : id;
		if (colon < 0 && !defaultNs.equals("minecraft")) {
			// unqualified references always mean the minecraft namespace in pack files
			ns = "minecraft";
		}
		String mapped = mapper.map(kind, ns, path);
		if (mapped == null || mapped.equals(path)) return null;
		return colon >= 0 ? ns + ":" + mapped : mapped;
	}

	private static boolean model(JsonElement json, String ns, Mapper mapper) {
		JsonObject o = Json.obj(json);
		if (o == null) return false;
		boolean changed = false;
		String parent = Json.str(o, "parent");
		if (parent != null) {
			String mapped = mapId(parent, Kind.MODEL, ns, mapper);
			if (mapped != null) {
				o.addProperty("parent", mapped);
				changed = true;
			}
		}
		JsonObject textures = Json.obj(o, "textures");
		if (textures != null) {
			for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
				String value = Json.str(e.getValue());
				if (value != null) {
					if (value.startsWith("#")) continue;
					String mapped = mapId(value, Kind.TEXTURE, ns, mapper);
					if (mapped != null) {
						e.setValue(new JsonPrimitive(mapped));
						changed = true;
					}
				} else {
					// 1.21.11+ texture objects: {"sprite": "...", "force_translucent": ...}
					JsonObject t = Json.obj(e.getValue());
					String sprite = Json.str(t, "sprite");
					if (sprite != null && !sprite.startsWith("#")) {
						String mapped = mapId(sprite, Kind.TEXTURE, ns, mapper);
						if (mapped != null) {
							t.addProperty("sprite", mapped);
							changed = true;
						}
					}
				}
			}
		}
		JsonArray overrides = Json.arr(o, "overrides");
		if (overrides != null) {
			for (JsonElement e : overrides) {
				JsonObject ov = Json.obj(e);
				String model = Json.str(ov, "model");
				if (model != null) {
					String mapped = mapId(model, Kind.MODEL, ns, mapper);
					if (mapped != null) {
						ov.addProperty("model", mapped);
						changed = true;
					}
				}
			}
		}
		return changed;
	}

	/** Blockstates and item definitions: every string value under a "model" key, at any depth. */
	private static boolean modelRefsAnywhere(JsonElement json, String ns, Mapper mapper) {
		boolean changed = false;
		if (json.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
				String value = Json.str(e.getValue());
				if (value != null && e.getKey().equals("model")) {
					String mapped = mapId(value, Kind.MODEL, ns, mapper);
					if (mapped != null) {
						e.setValue(new JsonPrimitive(mapped));
						changed = true;
					}
				} else if (!e.getValue().isJsonPrimitive()) {
					changed |= modelRefsAnywhere(e.getValue(), ns, mapper);
				}
			}
		} else if (json.isJsonArray()) {
			for (JsonElement e : json.getAsJsonArray()) changed |= modelRefsAnywhere(e, ns, mapper);
		}
		return changed;
	}

	private static boolean font(JsonElement json, String ns, Mapper mapper) {
		JsonArray providers = Json.arr(Json.obj(json), "providers");
		if (providers == null) return false;
		boolean changed = false;
		for (JsonElement e : providers) {
			JsonObject p = Json.obj(e);
			String file = Json.str(p, "file");
			if (file == null || !file.endsWith(".png")) continue;
			String mapped = mapId(file.substring(0, file.length() - 4), Kind.TEXTURE, ns, mapper);
			if (mapped != null) {
				p.addProperty("file", mapped + ".png");
				changed = true;
			}
		}
		return changed;
	}

	private static boolean particle(JsonElement json, String ns, Mapper mapper) {
		JsonArray textures = Json.arr(Json.obj(json), "textures");
		if (textures == null) return false;
		boolean changed = false;
		for (int i = 0; i < textures.size(); i++) {
			String id = Json.str(textures.get(i));
			if (id == null) continue;
			int colon = id.indexOf(':');
			String prefix = colon >= 0 ? id.substring(0, colon + 1) : "";
			String mapped = mapId(prefix + "particle/" + id.substring(colon + 1), Kind.TEXTURE, ns, mapper);
			if (mapped != null) {
				String path = mapped.substring(mapped.indexOf(':') + 1);
				if (path.startsWith("particle/")) {
					textures.set(i, new JsonPrimitive(prefix + path.substring("particle/".length())));
					changed = true;
				}
			}
		}
		return changed;
	}

	private static boolean atlas(JsonElement json, String ns, Mapper mapper) {
		JsonArray sources = Json.arr(Json.obj(json), "sources");
		if (sources == null) return false;
		boolean changed = false;
		for (JsonElement e : sources) {
			JsonObject s = Json.obj(e);
			String type = Json.str(s, "type");
			if (type == null || !(type.equals("single") || type.equals("minecraft:single"))) continue;
			String resource = Json.str(s, "resource");
			if (resource == null) continue;
			String mapped = mapId(resource, Kind.TEXTURE, ns, mapper);
			if (mapped != null) {
				s.addProperty("resource", mapped);
				changed = true;
			}
		}
		return changed;
	}

	private static boolean sounds(JsonElement json, String ns, Mapper mapper) {
		JsonObject events = Json.obj(json);
		if (events == null) return false;
		boolean changed = false;
		for (Map.Entry<String, JsonElement> event : events.entrySet()) {
			JsonArray list = Json.arr(Json.obj(event.getValue()), "sounds");
			if (list == null) continue;
			for (int i = 0; i < list.size(); i++) {
				JsonElement s = list.get(i);
				String name = Json.str(s);
				if (name != null) {
					String mapped = mapId(name, Kind.SOUND, ns, mapper);
					if (mapped != null) {
						list.set(i, new JsonPrimitive(mapped));
						changed = true;
					}
				} else {
					JsonObject o = Json.obj(s);
					String type = Json.str(o, "type");
					name = Json.str(o, "name");
					if (name == null || "event".equals(type)) continue;
					String mapped = mapId(name, Kind.SOUND, ns, mapper);
					if (mapped != null) {
						o.addProperty("name", mapped);
						changed = true;
					}
				}
			}
		}
		return changed;
	}
}
