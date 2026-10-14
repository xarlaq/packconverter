package dev.packconverter.core.steps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Conversions of non-texture data from before the Flattening: language files and OptiFine/MCPatcher folders. */
public final class LegacyDataSteps {
	private static Map<String, Map<String, String>> langKeys;
	private static Map<String, String> legacyIds;

	private LegacyDataSteps() {
	}

	/**
	 * 1.13 replaced "key=value" .lang files with JSON and renamed nearly every key (item.swordDiamond.name ->
	 * item.minecraft.diamond_sword); 1.14 renamed a few more. Language codes became lowercase (en_US -> en_us).
	 */
	public static Step lang113() {
		return new SimpleStep(4, "1.13 language files", (tree, ctx) -> {
			Map<String, String> keys113 = langKeys().get("1_13"), keys114 = langKeys().get("1_14");
			int converted = 0;
			for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/lang/") && p.endsWith(".lang"))) {
				byte[] data = tree.bytes(path);
				if (data == null) continue;
				JsonObject json = new JsonObject();
				String text = new String(data, StandardCharsets.UTF_8);
				if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
				for (String line : text.split("\r?\n")) {
					if (line.isBlank() || line.startsWith("#")) continue;
					int eq = line.indexOf('=');
					if (eq <= 0) continue;
					String key = line.substring(0, eq).trim(), value = line.substring(eq + 1);
					key = keys113.getOrDefault(key, key);
					key = keys114.getOrDefault(key, key);
					json.addProperty(key, value);
				}
				String dir = path.substring(0, path.lastIndexOf('/') + 1);
				String code = path.substring(dir.length(), path.length() - ".lang".length()).toLowerCase(Locale.ROOT);
				String target = dir + code + ".json";
				JsonObject existing = Json.obj(tree.json(target));
				if (existing != null) {
					// a newer-format file the pack already ships wins key by key
					for (Map.Entry<String, JsonElement> e : existing.entrySet()) json.add(e.getKey(), e.getValue());
				}
				tree.remove(path);
				tree.putJson(target, json);
				converted++;
			}
			ctx.count("language files converted", converted);
		});
	}

	/**
	 * OptiFine's predecessor MCPatcher kept connected textures, custom skies, CIT etc. in mcpatcher/; OptiFine (and
	 * the Fabric mods that read its formats) use optifine/. Properties files also named items and blocks by
	 * numeric id before 1.13, and texture paths inside them followed the folder renames.
	 */
	public static Step optifine113() {
		return new SimpleStep(4, "1.13 OptiFine folders", (tree, ctx) -> {
			Map<String, String> moves = new HashMap<>();
			for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/mcpatcher/"))) {
				String target = path.replaceFirst("/mcpatcher/", "/optifine/");
				if (!tree.has(target)) moves.put(path, target);
			}
			ctx.count("renamed files", tree.renameAll(moves, false));
			int changed = 0;
			for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/optifine/") && p.endsWith(".properties"))) {
				byte[] data = tree.bytes(path);
				if (data == null) continue;
				String text = new String(data, StandardCharsets.ISO_8859_1);
				StringBuilder out = new StringBuilder();
				boolean dirty = false;
				for (String line : text.split("\r?\n", -1)) {
					String fixed = fixProperty(line);
					dirty |= !fixed.equals(line);
					out.append(fixed).append('\n');
				}
				if (dirty) {
					tree.putBytes(path, out.toString().getBytes(StandardCharsets.ISO_8859_1));
					changed++;
				}
			}
			ctx.count("OptiFine properties updated", changed);
		});
	}

	private static String fixProperty(String line) {
		int eq = line.indexOf('=');
		if (eq <= 0 || line.startsWith("#")) return line;
		String key = line.substring(0, eq).trim(), value = line.substring(eq + 1).trim();
		if (key.equals("matchItems") || key.equals("items") || key.equals("matchBlocks") || key.equals("blocks")) {
			StringBuilder sb = new StringBuilder();
			for (String token : value.split("\\s+")) {
				if (token.isEmpty()) continue;
				String mapped = legacyIds().get(token);
				if (mapped == null && token.matches("\\d+(:\\d+)?") && token.contains(":")) mapped = legacyIds().get(token.substring(0, token.indexOf(':')));
				if (sb.length() > 0) sb.append(' ');
				sb.append(mapped != null ? mapped : token);
			}
			return key + "=" + sb;
		}
		if (value.contains("textures/blocks/") || value.contains("textures/items/")) {
			String[] parts = value.split("\\s+");
			for (int i = 0; i < parts.length; i++) {
				String p = parts[i];
				int start = p.indexOf("textures/");
				if (start < 0) continue;
				String prefix = p.substring(0, start), tex = p.substring(start);
				String withExt = tex.endsWith(".png") ? tex : tex + ".png";
				String moved = RenameStep.forward(withExt, null);
				parts[i] = prefix + (tex.endsWith(".png") ? moved : moved.substring(0, moved.length() - 4));
			}
			return key + "=" + String.join(" ", parts);
		}
		return line;
	}

	private static synchronized Map<String, Map<String, String>> langKeys() {
		if (langKeys == null) {
			Map<String, Map<String, String>> map = new HashMap<>();
			JsonObject root = Json.obj(Json.parse(resource("/packconverter/lang-keys.json")));
			for (String section : new String[]{"1_13", "1_14"}) {
				Map<String, String> m = new HashMap<>();
				for (Map.Entry<String, JsonElement> e : Json.obj(root, section).entrySet()) m.put(e.getKey(), e.getValue().getAsString());
				map.put(section, m);
			}
			langKeys = map;
		}
		return langKeys;
	}

	private static synchronized Map<String, String> legacyIds() {
		if (legacyIds == null) {
			Map<String, String> m = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : Json.obj(Json.obj(Json.parse(resource("/packconverter/legacy-ids.json"))), "ids").entrySet()) {
				m.put(e.getKey(), e.getValue().getAsString());
			}
			legacyIds = m;
		}
		return legacyIds;
	}

	private static byte[] resource(String name) {
		try (InputStream in = LegacyDataSteps.class.getResourceAsStream(name)) {
			if (in == null) throw new IllegalStateException(name + " missing from the mod jar");
			return in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
