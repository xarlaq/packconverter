package dev.packconverter.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.io.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * What a pack.mcmeta declares, read leniently (every historical shape: pack_format, supported_formats as int,
 * list or object, min_format/max_format as int or [major, minor], overlay entries with formats or min/max).
 *
 * @param format    the format the content was made for: pack_format if present, else the lowest declared one.
 *                  Steps that rewrite files in place are gated on this.
 * @param maxFormat the newest declared format; overlays are chosen for this one, since that's the most
 *                  up-to-date content the author provided.
 */
public record PackMeta(Format format, Format maxFormat, List<Overlay> overlays) {
	public record Format(int major, int minor) implements Comparable<Format> {
		public static Format of(int major) {
			return new Format(major, 0);
		}

		@Override
		public int compareTo(Format o) {
			int c = Integer.compare(major, o.major);
			return c != 0 ? c : Integer.compare(minor, o.minor);
		}

		@Override
		public String toString() {
			return minor == 0 ? Integer.toString(major) : major + "." + minor;
		}
	}

	public record Overlay(String directory, Format min, Format max) {
		public boolean matches(Format f) {
			return f.compareTo(min) >= 0 && f.compareTo(max) <= 0;
		}
	}

	/** Parses pack.mcmeta bytes; null if there is no usable format information at all. */
	public static PackMeta parse(byte[] mcmeta) {
		JsonObject root = Json.obj(Json.parse(mcmeta));
		JsonObject pack = Json.obj(root, "pack");
		if (pack == null) return null;
		Format packFormat = format(pack.get("pack_format"), false);
		Format[] supported = range(pack.get("supported_formats"));
		Format min = format(pack.get("min_format"), false);
		Format max = format(pack.get("max_format"), true);

		Format low = packFormat;
		if (low == null && min != null) low = min;
		if (low == null && supported != null) low = supported[0];
		if (low == null) return null;
		Format high = low;
		for (Format f : new Format[]{packFormat, max, supported != null ? supported[1] : null, min}) {
			if (f != null && f.compareTo(high) > 0) high = f;
		}

		List<Overlay> overlays = new ArrayList<>();
		JsonArray entries = Json.arr(Json.obj(root, "overlays"), "entries");
		if (entries != null) {
			for (JsonElement e : entries) {
				JsonObject o = Json.obj(e);
				String dir = Json.str(o, "directory");
				if (dir == null) continue;
				Format oMin = format(o.get("min_format"), false), oMax = format(o.get("max_format"), true);
				Format[] formats = range(o.get("formats"));
				if (oMin == null && formats != null) oMin = formats[0];
				if (oMax == null && formats != null) oMax = formats[1];
				if (oMin == null || oMax == null) continue;
				overlays.add(new Overlay(dir, oMin, oMax));
			}
		}
		return new PackMeta(low, high, overlays);
	}

	private static Format format(JsonElement e, boolean top) {
		if (e == null) return null;
		Double n = Json.num(e);
		if (n != null) return new Format(n.intValue(), top ? Integer.MAX_VALUE : 0);
		JsonArray a = Json.arr(e);
		if (a != null && !a.isEmpty()) {
			Double major = Json.num(a.get(0));
			if (major == null) return null;
			Double minor = a.size() > 1 ? Json.num(a.get(1)) : null;
			return new Format(major.intValue(), minor != null ? minor.intValue() : (top ? Integer.MAX_VALUE : 0));
		}
		return null;
	}

	private static Format[] range(JsonElement e) {
		if (e == null) return null;
		Double n = Json.num(e);
		if (n != null) return new Format[]{Format.of(n.intValue()), new Format(n.intValue(), Integer.MAX_VALUE)};
		JsonArray a = Json.arr(e);
		if (a != null && a.size() >= 2) {
			Double lo = Json.num(a.get(0)), hi = Json.num(a.get(1));
			if (lo != null && hi != null) return new Format[]{Format.of(lo.intValue()), new Format(hi.intValue(), Integer.MAX_VALUE)};
		}
		JsonObject o = Json.obj(e);
		if (o != null) {
			Double lo = Json.num(o.get("min_inclusive")), hi = Json.num(o.get("max_inclusive"));
			if (lo != null && hi != null) return new Format[]{Format.of(lo.intValue()), new Format(hi.intValue(), Integer.MAX_VALUE)};
		}
		return null;
	}
}
