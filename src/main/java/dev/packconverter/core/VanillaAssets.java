package dev.packconverter.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.io.Json;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Read access to the target version's own (26.3) client resources, e.g. to check which properties a block has
 * or which model an item uses now. In game this is the vanilla resource pack; in tests, extracted assets.
 */
public interface VanillaAssets {
	/** The file at a pack path like "assets/minecraft/models/item/generated.json", or null. */
	byte[] read(String path);

	VanillaAssets NONE = path -> null;

	/** Caching wrapper with JSON helpers. */
	final class Cached implements VanillaAssets {
		private static final Object MISSING = new Object();
		private final VanillaAssets delegate;
		private final Map<String, Object> json = new ConcurrentHashMap<>();

		public Cached(VanillaAssets delegate) {
			this.delegate = delegate;
		}

		@Override
		public byte[] read(String path) {
			return delegate.read(path);
		}

		/** Parsed JSON (shared: don't modify, deepCopy first). */
		public JsonElement json(String path) {
			Object v = json.computeIfAbsent(path, p -> {
				JsonElement e = Json.parse(delegate.read(p));
				return e == null ? MISSING : e;
			});
			return v == MISSING ? null : (JsonElement) v;
		}

		/** True if the file exists (JSON files are answered from the parse cache). */
		public boolean exists(String path) {
			if (path.endsWith(".json")) return json(path) != null;
			return delegate.read(path) != null;
		}

		/** A model's own JSON by id ("item/generated" or "minecraft:item/generated"). */
		public JsonObject model(String id) {
			int colon = id.indexOf(':');
			String ns = colon >= 0 ? id.substring(0, colon) : "minecraft";
			return Json.obj(json("assets/" + ns + "/models/" + id.substring(colon + 1) + ".json"));
		}
	}
}
