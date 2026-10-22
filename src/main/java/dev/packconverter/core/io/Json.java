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

	/** Whether the game reads this JSON as it is: strict Gson (BOM aside), one value and nothing after it. */
	public static boolean strict(byte[] data) {
		try {
			JsonReader reader = new JsonReader(new StringReader(new String(data, StandardCharsets.UTF_8)));
			reader.setStrictness(Strictness.STRICT);
			com.google.gson.JsonParser.parseReader(reader);
			return reader.peek() == com.google.gson.stream.JsonToken.END_DOCUMENT;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Read the way Minecraft 1.8 read pack JSON (lenient, its escapes; {@link #parse}), also with commas before a
	 * closing bracket or brace (which Gson takes for empty entries, or rejects), and without the empty entries extra
	 * commas leave in lists: what a file the game now rejects meant. Null if unreadable even so.
	 */
	public static JsonElement parseLoose(byte[] data) {
		if (data == null) return null;
		JsonElement json = parse(dropTrailingCommas(new String(data, StandardCharsets.UTF_8)));
		return json == null ? null : withoutNulls(json);
	}

	/** The text without commas that only have whitespace before a closing bracket or brace (strings left alone). */
	static String dropTrailingCommas(String text) {
		StringBuilder out = null;
		int copied = 0;
		boolean inString = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (inString) {
				if (c == '\\') i++;
				else if (c == '"') inString = false;
				continue;
			}
			if (c == '"') {
				inString = true;
				continue;
			}
			if (c != ',') continue;
			int next = i + 1;
			while (next < text.length() && Character.isWhitespace(text.charAt(next))) next++;
			if (next < text.length() && (text.charAt(next) == ']' || text.charAt(next) == '}')) {
				if (out == null) out = new StringBuilder(text.length());
				out.append(text, copied, i);
				copied = i + 1;
			}
		}
		return out == null ? text : out.append(text, copied, text.length()).toString();
	}

	/** The same JSON without null entries in lists (left by extra commas); nulls as object values stay. */
	static JsonElement withoutNulls(JsonElement json) {
		if (json.isJsonArray()) {
			JsonArray out = new JsonArray();
			for (JsonElement e : json.getAsJsonArray()) if (!e.isJsonNull()) out.add(withoutNulls(e));
			return out;
		}
		if (json.isJsonObject()) {
			JsonObject out = new JsonObject();
			for (var e : json.getAsJsonObject().entrySet()) out.add(e.getKey(), withoutNulls(e.getValue()));
			return out;
		}
		return json;
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
