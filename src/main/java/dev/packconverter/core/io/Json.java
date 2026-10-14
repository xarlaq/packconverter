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
			JsonReader reader = new JsonReader(new StringReader(text));
			reader.setStrictness(Strictness.LENIENT);
			JsonElement element = com.google.gson.JsonParser.parseReader(reader);
			return element == null || element.isJsonNull() ? null : element;
		} catch (Exception e) {
			return null;
		}
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
