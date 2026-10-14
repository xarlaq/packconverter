package dev.packconverter.core.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Up to 1.19.2 every texture a model named was stitched into the block atlas, wherever it lived (packs keep
 * custom swords in textures/custom/...). 1.19.3 only stitches the folders listed in atlases/*.json, and since
 * 1.21.11 item models use a separate items atlas: block models need their textures in the blocks atlas, item
 * models need all of theirs in one atlas. Textures the pack's models use that no atlas would pick up are added to
 * the pack's atlases/blocks.json or atlases/items.json.
 * <p>
 * The other way round, 1.19.3 stitches every file in block/, including ones no model used, which old versions never
 * loaded. A leftover whose frames aren't a multiple of 16 pixels would turn mipmapping off for every block, so such
 * unused files are left out.
 */
public final class AtlasStep implements Step {
	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "atlases";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		if (!ctx.before(73)) return;
		if (ctx.before(12)) dropUnusedOddBlockTextures(tree, ctx);
		Atlas blocks = new Atlas(), items = new Atlas();
		blocks.read(ctx.vanilla().json("assets/minecraft/atlases/blocks.json"));
		items.read(ctx.vanilla().json("assets/minecraft/atlases/items.json"));
		blocks.read(tree.json("assets/minecraft/atlases/blocks.json"));
		items.read(tree.json("assets/minecraft/atlases/items.json"));

		Set<String> addBlocks = new TreeSet<>(), addItems = new TreeSet<>();
		for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/models/") && p.endsWith(".json"))) {
			JsonObject textures = Json.obj(Json.obj(tree.json(path)), "textures");
			if (textures == null) continue;
			List<String> ids = new ArrayList<>();
			for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
				String v = Json.str(e.getValue());
				if (v == null) v = Json.str(Json.obj(e.getValue()), "sprite");
				if (v == null || v.startsWith("#")) continue;
				String id = v.contains(":") ? v : "minecraft:" + v;
				if (tree.has(texturePath(id))) ids.add(id); // only textures the pack provides can be missing from vanilla's lists
			}
			if (ids.isEmpty()) continue;
			boolean blockModel = path.split("/", 4)[3].startsWith("models/block/");
			if (blockModel) {
				for (String id : ids) if (!blocks.covers(id)) addBlocks.add(id);
				continue;
			}
			boolean allItems = true, allBlocks = true, anyBlocks = false;
			for (String id : ids) {
				boolean inItems = items.covers(id) || addItems.contains(id), inBlocks = blocks.covers(id) || addBlocks.contains(id);
				allItems &= inItems;
				allBlocks &= inBlocks;
				anyBlocks |= inBlocks && !inItems;
			}
			if (allItems || allBlocks) continue;
			for (String id : ids) {
				if (anyBlocks) {
					if (!blocks.covers(id)) addBlocks.add(id);
				} else if (!items.covers(id)) {
					addItems.add(id);
				}
			}
		}
		add(tree, "assets/minecraft/atlases/blocks.json", addBlocks);
		add(tree, "assets/minecraft/atlases/items.json", addItems);
		ctx.count("textures added to atlases", addBlocks.size() + addItems.size());
	}

	/** Leftover block textures (no vanilla name, no model uses them) with frames too small or odd for mipmaps. */
	private static void dropUnusedOddBlockTextures(PackTree tree, Context ctx) {
		Set<String> used = new HashSet<>();
		for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/models/") && p.endsWith(".json"))) {
			JsonObject textures = Json.obj(Json.obj(tree.json(path)), "textures");
			if (textures == null) continue;
			for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
				String v = Json.str(e.getValue());
				if (v == null) v = Json.str(Json.obj(e.getValue()), "sprite");
				if (v != null && !v.startsWith("#")) used.add(texturePath(v.contains(":") ? v : "minecraft:" + v));
			}
		}
		List<String> unused = tree.paths(AtlasStep::isBlockTexture).stream().filter(p -> !used.contains(p)).toList();
		// image headers on all cores: one small read per file adds up over a pack's thousand block textures
		Map<String, int[]> sizes = new ConcurrentHashMap<>();
		unused.parallelStream().forEach(p -> {
			int[] size = tree.imageSize(p);
			if (size != null) sizes.put(p, size);
		});
		int dropped = 0;
		for (String path : unused) {
			int[] frame = frameSize(tree, path, sizes.get(path));
			if (frame != null && Math.min(Integer.lowestOneBit(frame[0]), Integer.lowestOneBit(frame[1])) >= 16) continue;
			if (ctx.vanilla().exists(path)) continue; // checked last: a lookup per file is slow
			tree.remove(path);
			tree.remove(path + ".mcmeta");
			dropped++;
		}
		ctx.count("unused odd block textures left out", dropped);
	}

	/** assets/<namespace>/textures/block/...png: what the blocks atlas stitches. */
	private static boolean isBlockTexture(String path) {
		int ns = path.indexOf('/', 7);
		return path.startsWith("assets/") && ns > 0 && path.startsWith("/textures/block/", ns) && path.endsWith(".png");
	}

	/** A sprite's frame size the way 26.3 reads it; null if the frames don't fit the image. */
	private static int[] frameSize(PackTree tree, String path, int[] size) {
		if (size == null) return null;
		JsonObject animation = Json.obj(Json.obj(tree.json(path + ".mcmeta")), "animation");
		if (animation == null) return size;
		Double w = Json.num(animation.get("width")), h = Json.num(animation.get("height"));
		int fw, fh;
		if (w == null && h == null) fw = fh = Math.min(size[0], size[1]);
		else {
			fw = w != null ? w.intValue() : size[0];
			fh = h != null ? h.intValue() : size[1];
		}
		if (fw <= 0 || fh <= 0 || size[0] % fw != 0 || size[1] % fh != 0) return null;
		return new int[]{fw, fh};
	}

	private static String texturePath(String id) {
		int colon = id.indexOf(':');
		return "assets/" + id.substring(0, colon) + "/textures/" + id.substring(colon + 1) + ".png";
	}

	private static void add(PackTree tree, String path, Set<String> ids) {
		if (ids.isEmpty()) return;
		JsonObject atlas = Json.obj(tree.json(path));
		if (atlas == null) atlas = new JsonObject();
		JsonArray sources = Json.arr(atlas, "sources");
		if (sources == null) {
			sources = new JsonArray();
			atlas.add("sources", sources);
		}
		for (String id : ids) {
			JsonObject single = new JsonObject();
			single.addProperty("type", "minecraft:single");
			single.addProperty("resource", id);
			sources.add(single);
		}
		tree.putJson(path, atlas);
	}

	/** The sprites an atlas config stitches: folders (by prefix) and single textures. */
	private static final class Atlas {
		private final List<String> directories = new ArrayList<>(); // "minecraft:block/"
		private final Set<String> singles = new HashSet<>();

		void read(JsonElement json) {
			JsonArray sources = Json.arr(Json.obj(json), "sources");
			if (sources == null) return;
			for (JsonElement e : sources) {
				JsonObject s = Json.obj(e);
				String type = Json.str(s, "type");
				if (type == null) continue;
				type = type.startsWith("minecraft:") ? type.substring(10) : type;
				if (type.equals("directory")) {
					String source = Json.str(s, "source"), prefix = Json.str(s, "prefix");
					// sprites keep their path when the prefix is the folder itself (vanilla's convention)
					if (source != null && (prefix == null || prefix.equals(source + "/"))) directories.add(source + "/");
				} else if (type.equals("single")) {
					String resource = Json.str(s, "resource");
					if (resource != null) singles.add(resource.contains(":") ? resource : "minecraft:" + resource);
				}
			}
		}

		boolean covers(String id) {
			if (singles.contains(id)) return true;
			String path = id.substring(id.indexOf(':') + 1);
			for (String dir : directories) if (path.startsWith(dir)) return true;
			return false;
		}
	}
}
