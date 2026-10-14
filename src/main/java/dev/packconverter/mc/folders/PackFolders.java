package dev.packconverter.mc.folders;

import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.Lunar;

import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Folders in the resource pack screen: plain folders in resourcepacks/, nothing written into them. A pack's folder
 * comes from its id: packs found in subfolders are "file/&lt;folder&gt;/&lt;pack&gt;" ({@link NestedPackSource}), the
 * others sit at the top. The folder being shown is remembered while the game runs.
 */
public final class PackFolders {
	public static final Comparator<String> ORDER = String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
	private static String current = "";

	private PackFolders() {
	}

	/** Off on Lunar Client, which has folders of its own (its pack manager replaces the screen's lists). */
	public static boolean enabled() {
		return !Lunar.detected() && PackConverterConfig.get().packFolders;
	}

	/** "" for the top level. */
	public static String current() {
		return current;
	}

	public static void setCurrent(String folder) {
		current = folder;
	}

	/** The folder a pack is in: "" for top-level and built-in packs. */
	public static String folderOf(String packId) {
		if (!packId.startsWith("file/")) return "";
		String path = packId.substring("file/".length());
		int slash = path.lastIndexOf('/');
		return slash < 0 ? "" : path.substring(0, slash);
	}

	public static String parent(String folder) {
		int slash = folder.lastIndexOf('/');
		return slash < 0 ? "" : folder.substring(0, slash);
	}

	public static String name(String folder) {
		return folder.substring(folder.lastIndexOf('/') + 1);
	}

	/** True if any pack is in this folder or below it. */
	public static boolean exists(String folder, Stream<String> packIds) {
		return folder.isEmpty() || packIds.map(PackFolders::folderOf).anyMatch(f -> f.equals(folder) || f.startsWith(folder + "/"));
	}

	/** The folders directly inside {@code folder}, each with the number of packs anywhere inside it. */
	public static Map<String, Integer> subfolders(String folder, Stream<String> packIds) {
		String prefix = folder.isEmpty() ? "" : folder + "/";
		Map<String, Integer> result = new TreeMap<>(ORDER);
		packIds.map(PackFolders::folderOf).forEach(f -> {
			if (f.isEmpty() || !f.startsWith(prefix) || f.length() == prefix.length()) return;
			int slash = f.indexOf('/', prefix.length());
			result.merge(slash < 0 ? f : f.substring(0, slash), 1, Integer::sum);
		});
		return result;
	}
}
