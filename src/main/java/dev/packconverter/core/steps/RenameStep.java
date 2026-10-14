package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Refs;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies one version's file renames from the generated table (resources/packconverter/renames.json, built
 * from vanilla asset diffs by tools/BuildTables.java), then rewrites references to the renamed files.
 * Renames never overwrite a file the pack already has under the new name: a pack that ships both keeps its
 * newer file. Emissive/normal/specular companions ("_e", "_n", "_s") follow their texture.
 */
public final class RenameStep implements Step {
	private static final String[] COMPANIONS = {"_e", "_n", "_s"};
	private static Map<String, RenameStep> table;

	private final String version;
	private final int format;
	private final Map<String, List<String>> files;

	private RenameStep(String version, int format, Map<String, List<String>> files) {
		this.version = version;
		this.format = format;
		this.files = files;
	}

	/** The step for the update to {@code version} (e.g. "1.13.2"). */
	public static synchronized RenameStep to(String version) {
		if (table == null) table = load();
		RenameStep step = table.get(version);
		if (step == null) throw new IllegalArgumentException("No rename table for " + version);
		return step;
	}

	private static Map<String, RenameStep> load() {
		try (InputStream in = RenameStep.class.getResourceAsStream("/packconverter/renames.json")) {
			if (in == null) throw new IllegalStateException("renames.json missing from the mod jar");
			JsonObject root = Json.obj(Json.parse(in.readAllBytes()));
			Map<String, RenameStep> steps = new LinkedHashMap<>();
			for (JsonElement e : Json.arr(root, "steps")) {
				JsonObject s = e.getAsJsonObject();
				Map<String, List<String>> files = new LinkedHashMap<>();
				for (Map.Entry<String, JsonElement> f : Json.obj(s, "files").entrySet()) {
					List<String> targets = new ArrayList<>();
					f.getValue().getAsJsonArray().forEach(t -> targets.add(t.getAsString()));
					files.put(f.getKey(), targets);
				}
				String id = s.get("id").getAsString();
				steps.put(id, new RenameStep(id, s.get("format").getAsInt(), files));
			}
			return steps;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Where a vanilla file (path under assets/minecraft/) ended up in the newest version, following every rename. */
	public static String forward(String path) {
		return forward(path, null);
	}

	/** Like {@link #forward(String)} for a path from version {@code since} (only later renames apply). */
	public static synchronized String forward(String path, String since) {
		if (table == null) table = load();
		String p = path;
		boolean active = since == null;
		for (RenameStep step : table.values()) {
			if (active) {
				List<String> to = step.files.get(p);
				if (to != null) p = to.getFirst();
			} else if (step.version.equals(since)) {
				active = true;
			}
		}
		return p;
	}

	/** Every table version, oldest first. */
	public static synchronized List<String> ids() {
		if (table == null) table = load();
		return List.copyOf(table.keySet());
	}

	/** The path this step renamed into {@code path}, or the path itself. */
	public String unrename(String path) {
		for (Map.Entry<String, List<String>> e : files.entrySet()) {
			if (e.getValue().getFirst().equals(path)) return e.getKey();
		}
		return path;
	}

	/** Where this step moved a path (under assets/minecraft/), or the path itself. */
	public String rename(String path) {
		List<String> to = files.get(path);
		return to != null ? to.getFirst() : path;
	}

	@Override
	public int format() {
		return format;
	}

	@Override
	public String name() {
		return version + " renames";
	}

	public Map<String, List<String>> files() {
		return files;
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		String root = "assets/minecraft/";
		Map<String, String> renames = new HashMap<>();
		Map<String, List<String>> copies = new HashMap<>();
		Map<String, String> textures = new HashMap<>(), models = new HashMap<>(), sounds = new HashMap<>();
		for (Map.Entry<String, List<String>> e : files.entrySet()) {
			String from = e.getKey();
			List<String> to = e.getValue();
			// references are rewritten even when the pack doesn't contain the file (it may point at vanilla's)
			idPair(from, to.getFirst(), "textures/", ".png", textures);
			idPair(from, to.getFirst(), "models/", ".json", models);
			idPair(from, to.getFirst(), "sounds/", ".ogg", sounds);
			if (!tree.has(root + from)) continue;
			renames.put(root + from, root + to.getFirst());
			if (to.size() > 1) copies.put(root + to.getFirst(), to.subList(1, to.size()).stream().map(t -> root + t).toList());
			if (from.endsWith(".png")) {
				// the texture's own animation/scaling metadata goes with it
				if (tree.has(root + from + ".mcmeta") && !files.containsKey(from + ".mcmeta")) {
					renames.put(root + from + ".mcmeta", root + to.getFirst() + ".mcmeta");
					if (to.size() > 1) copies.put(root + to.getFirst() + ".mcmeta", to.subList(1, to.size()).stream().map(t -> root + t + ".mcmeta").toList());
				}
				String base = from.substring(0, from.length() - 4), target = to.getFirst().substring(0, to.getFirst().length() - 4);
				for (String suffix : COMPANIONS) {
					for (String ext : new String[]{".png", ".png.mcmeta"}) {
						String companion = root + base + suffix + ext;
						if (tree.has(companion) && !files.containsKey(base + suffix + ext)) {
							renames.put(companion, root + target + suffix + ext);
						}
					}
				}
			}
		}
		int moved = tree.renameAll(renames, false);
		int copied = 0;
		for (Map.Entry<String, List<String>> e : copies.entrySet()) {
			if (!tree.has(e.getKey())) continue;
			for (String target : e.getValue()) {
				if (!tree.has(target) && tree.copy(e.getKey(), target)) copied++;
			}
		}
		ctx.count("renamed files", moved + copied);
		int refs = Refs.rewrite(tree, Refs.of(textures, models, sounds));
		ctx.count("files with updated references", refs);
	}

	private static void idPair(String from, String to, String prefix, String suffix, Map<String, String> out) {
		if (from.startsWith(prefix) && from.endsWith(suffix) && to.startsWith(prefix) && to.endsWith(suffix)) {
			out.put(from.substring(prefix.length(), from.length() - suffix.length()), to.substring(prefix.length(), to.length() - suffix.length()));
		}
	}
}
