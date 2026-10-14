package dev.packconverter.core;

import java.util.Map;
import java.util.TreeMap;

/** Which game versions a resource pack format number belongs to, for showing to players. */
public final class PackFormats {
	private static final TreeMap<Integer, String> RELEASES = new TreeMap<>(Map.ofEntries(
		Map.entry(1, "1.6 – 1.8"), Map.entry(2, "1.9 – 1.10"), Map.entry(3, "1.11 – 1.12"), Map.entry(4, "1.13 – 1.14"),
		Map.entry(5, "1.15 – 1.16.1"), Map.entry(6, "1.16.2 – 1.16.5"), Map.entry(7, "1.17"), Map.entry(8, "1.18"),
		Map.entry(9, "1.19 – 1.19.2"), Map.entry(12, "1.19.3"), Map.entry(13, "1.19.4"), Map.entry(15, "1.20 – 1.20.1"),
		Map.entry(18, "1.20.2"), Map.entry(22, "1.20.3 – 1.20.4"), Map.entry(32, "1.20.5 – 1.20.6"), Map.entry(34, "1.21 – 1.21.1"),
		Map.entry(42, "1.21.2 – 1.21.3"), Map.entry(46, "1.21.4"), Map.entry(55, "1.21.5"), Map.entry(63, "1.21.6"),
		Map.entry(64, "1.21.7 – 1.21.8"), Map.entry(69, "1.21.9 – 1.21.10"), Map.entry(75, "1.21.11"), Map.entry(84, "26.1"),
		Map.entry(88, "26.2"), Map.entry(97, "26.3")));

	private PackFormats() {
	}

	/** "1.6 – 1.8" for format 1; snapshot-only formats name the nearest release before them ("1.21.11 snapshot"). */
	public static String describe(PackMeta.Format format) {
		int major = format.major();
		String exact = RELEASES.get(major);
		if (exact != null) return exact;
		Map.Entry<Integer, String> below = RELEASES.floorEntry(major);
		if (below == null) return "format " + major;
		Map.Entry<Integer, String> above = RELEASES.higherEntry(major);
		return above != null ? above.getValue() + " snapshot" : "newer than " + below.getValue();
	}
}
