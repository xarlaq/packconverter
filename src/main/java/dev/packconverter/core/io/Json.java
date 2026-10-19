package dev.packconverter.core.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;

/** Lenient JSON for hand-written pack files (BOMs, comments, trailing commas, single quotes). */
public final class Json {
	public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	public static final Gson PRETTY = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	private Json() {
	}

	public static JsonElement parse(byte[] data) {
		if (data == null) return null;
		return parse(new String(data, StandardCharsets.UTF_8));
	}

	public static JsonElement parse(String text) {
		if (text == null) return null;
		if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
		try {
			return parseLenient(text);
		} catch (Exception e) {
			// old packs' escapes Gson no longer takes ("\!"): read them the way the game once did
			String fixed = fixEscapes(text);
			if (fixed == text) return null;
			try {
				return parseLenient(fixed);
			} catch (Exception again) {
				return null;
			}
		}
	}

	private static JsonElement parseLenient(String text) {
		JsonReader reader = new JsonReader(new StringReader(text));
		reader.setStrictness(Strictness.LENIENT);
		JsonElement element = com.google.gson.JsonParser.parseReader(reader);
		return element == null || element.isJsonNull() ? null : element;
	}

	/**
	 * The text with escapes JSON doesn't have ({@code \!}, {@code \ }) read as the character escaped, the way the Gson of
	 * Minecraft 1.8 and before did, so pack.mcmeta descriptions like {@code "§b\! made by"} were fine then; Gson
	 * now rejects the whole file. The same string when there are none.
	 */
	public static String fixEscapes(String text) {
		StringBuilder out = null;
		int copied = 0;
		for (int i = 0; i < text.length() - 1; i++) {
			if (text.charAt(i) != '\\') continue;
			if ("\"\\/bfnrtu'".indexOf(text.charAt(i + 1)) < 0) {
				if (out == null) out = new StringBuilder(text.length());
				out.append(text, copied, i); // the backslash is dropped, the character after it kept
				copied = i + 1;
			}
			i++; // an escaped character never starts another escape ("\\!" stays)
		}
		return out == null ? text : out.append(text, copied, text.length()).toString();
	}

	public static byte[] toBytes(JsonElement json) {
		return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
	}

	public static JsonObject obj(JsonElement e) {
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	public static JsonArray arr(JsonElement e) {
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	public static String str(JsonElement e) {
		return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : null;
	}

	public static String str(JsonObject o, String key) {
		return o == null ? null : str(o.get(key));
	}

	public static JsonObject obj(JsonObject o, String key) {
		return o == null ? null : obj(o.get(key));
	}

	public static JsonArray arr(JsonObject o, String key) {
		return o == null ? null : arr(o.get(key));
	}

	public static Double num(JsonElement e) {
		if (e == null || !e.isJsonPrimitive()) return null;
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isNumber()) return p.getAsDouble();
		if (p.isString()) {
			try {
				return Double.parseDouble(p.getAsString().trim());
			} catch (NumberFormatException ignored) {
				return null;
			}
		}
		if (p.isBoolean()) return p.getAsBoolean() ? 1.0 : 0.0;
		return null;
	}
}
