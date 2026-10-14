package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;

/**
 * Many packs ship every vanilla model and blockstate of their version unchanged (they started from the default
 * pack). Those copies customise nothing, but their old structure breaks on the new version (renamed texture
 * slots, merged blocks, removed parents). Files identical to a vanilla file of any version at the same path are
 * removed, so the target version's own files apply; every texture still comes from the pack.
 * The fingerprints (path + canonical JSON) come from tools/BuildVanillaIndex.java.
 */
public final class VanillaCopiesStep implements Step {
	private static long[] hashes;

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "unchanged vanilla copies";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		// tests that check how vanilla-shaped files convert turn this off
		if (Boolean.getBoolean("packconverter.keepVanillaCopies")) return;
		long[] index = index();
		int removed = 0;
		for (String path : tree.paths("assets/")) {
			if (!path.endsWith(".json")) continue;
			String[] parts = path.split("/");
			if (parts.length < 4) continue;
			String kind = parts[2];
			if (!(kind.equals("models") || kind.equals("blockstates") || kind.equals("items") || kind.equals("atlases")
				|| kind.equals("particles") || kind.equals("font") || kind.equals("equipment"))) continue;
			JsonElement json = tree.json(path);
			if (json == null) continue;
			if (Arrays.binarySearch(index, hash(path, canonical(json))) >= 0) {
				tree.remove(path);
				removed++;
			}
		}
		ctx.count("unchanged vanilla files dropped", removed);
	}

	private static synchronized long[] index() {
		if (hashes == null) {
			try (InputStream raw = VanillaCopiesStep.class.getResourceAsStream("/packconverter/vanilla-json.bin");
				 DataInputStream in = new DataInputStream(new GZIPInputStream(raw))) {
				long[] h = new long[in.readInt()];
				for (int i = 0; i < h.length; i++) h[i] = in.readLong();
				Arrays.sort(h);
				hashes = h;
			} catch (IOException | NullPointerException e) {
				throw new IllegalStateException("vanilla-json.bin missing from the mod jar", e);
			}
		}
		return hashes;
	}

	/** Must match tools/BuildVanillaIndex.java exactly. */
	static String canonical(JsonElement e) {
		if (e.isJsonObject()) {
			StringBuilder sb = new StringBuilder("{");
			Map<String, JsonElement> sorted = new TreeMap<>(e.getAsJsonObject().asMap());
			sorted.remove("__comment");
			sorted.forEach((k, v) -> sb.append('"').append(k).append("\":").append(canonical(v)).append(','));
			return sb.append('}').toString();
		}
		if (e.isJsonArray()) {
			StringBuilder sb = new StringBuilder("[");
			e.getAsJsonArray().forEach(v -> sb.append(canonical(v)).append(','));
			return sb.append(']').toString();
		}
		if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) return Double.toString(e.getAsDouble());
		return e.toString();
	}

	static long hash(String path, String canonical) {
		try {
			byte[] d = MessageDigest.getInstance("SHA-1").digest((path + "\n" + canonical).getBytes(StandardCharsets.UTF_8));
			long h = 0;
			for (int i = 0; i < 8; i++) h = (h << 8) | (d[i] & 0xFF);
			return h;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
