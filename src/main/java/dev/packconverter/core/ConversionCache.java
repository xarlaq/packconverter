package dev.packconverter.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.io.Json;
import dev.packconverter.core.source.PackSource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Stores conversions on disk so each pack version is converted once. A cached conversion is one zip,
 * {@code <name>-<key>.zip}, holding an index (converted path -> source path, or generated) and only the files the
 * conversion created or changed; untouched files are read from the original pack. The key covers the pack's
 * fingerprint (path, size, modification time), {@link Converter#VERSION} and the {@link Options}.
 * <p>
 * The cache is kept under a size limit: when it grows past it, the conversions used longest ago are deleted
 * (a zip's modification time is its last use). Conversions of packs that no longer exist are deleted too.
 */
public final class ConversionCache {
	public static final String GENERATED = "";
	private static final String SOURCE = "source.txt", INDEX = "index.json", REPORT = "report.txt", FILES = "files/";

	/** A converted pack: path -> source path, or {@link #GENERATED} for files stored in {@code zip}. */
	public record Entry(Path file, ZipFile zip, NavigableMap<String, String> files, boolean fresh, Converter.Result result) {
		public InputStream openGenerated(String path) throws IOException {
			ZipEntry e = zip.getEntry(FILES + path);
			if (e == null) throw new java.io.FileNotFoundException(path + " in " + file);
			return zip.getInputStream(e);
		}
	}

	private final Path root;
	private final LongSupplier maxBytes;
	private final Supplier<Options> options;
	private final Map<Path, Entry> loaded = new ConcurrentHashMap<>();

	/**
	 * @param maxBytes size limit, read each time it's enforced (so config changes apply at once)
	 * @param options the choices conversions are made with, read for each pack (part of the key: changing them
	 *                converts packs again the next time they load)
	 */
	public ConversionCache(Path root, LongSupplier maxBytes, Supplier<Options> options) {
		this.root = root;
		this.maxBytes = maxBytes;
		this.options = options;
	}

	public ConversionCache(Path root) {
		this(root, () -> Long.MAX_VALUE, () -> Options.PLAIN);
	}

	/** The choices this cache's conversions are made with now. */
	public Options options() {
		return options.get();
	}

	public Path root() {
		return root;
	}

	/**
	 * Loads the cached conversion of {@code source}, or converts and stores it.
	 *
	 * @param location where the pack lives, remembered so conversions of deleted packs can be cleaned up
	 */
	public Entry get(String packName, Path location, PackSource source, PackMeta meta, VanillaAssets vanilla) throws IOException {
		return this.get(packName, location, source, meta, vanilla, true);
	}

	/**
	 * As {@link #get(String, Path, PackSource, PackMeta, VanillaAssets)}; with {@code keep} false (one-off use, like
	 * exports) a conversion read or made here isn't kept open: pass it to {@link #release} when done. One the game
	 * already has open is shared, and release leaves it alone.
	 */
	public synchronized Entry get(String packName, Path location, PackSource source, PackMeta meta, VanillaAssets vanilla, boolean keep)
		throws IOException {
		String base = safeName(packName);
		Options options = this.options.get();
		Path file = root.resolve(base + "-" + key(source.fingerprint() + options.key()) + ".zip");
		Entry cached = loaded.get(file);
		if (cached != null) return cached;
		if (Files.isRegularFile(file)) {
			Entry entry = read(file);
			if (entry != null) {
				touch(file);
				if (keep) loaded.put(file, entry);
				return entry;
			}
			Files.deleteIfExists(file); // older converter or damaged
		}
		Converter.Result result = Converter.convert(source, meta, vanilla, options);
		write(file, location, result);
		Entry stored = read(file);
		if (stored == null) throw new IOException("Could not read back " + file);
		if (keep) loaded.put(file, stored);
		deleteStale(base, file);
		enforceLimit();
		return new Entry(stored.file(), stored.zip(), stored.files(), true, result);
	}

	/**
	 * The report of this pack's cached conversion ("Converted from format ... in ... ms", "counter: n", "warning: ..."
	 * lines), or null if it hasn't been converted (by this version of the converter).
	 */
	public List<String> report(String packName, PackSource source) throws IOException {
		Path file = root.resolve(safeName(packName) + "-" + key(source.fingerprint() + options.get().key()) + ".zip");
		Entry entry = loaded.get(file);
		if (entry != null) return readReport(entry.zip());
		if (!Files.isRegularFile(file)) return null;
		try (ZipFile zip = new ZipFile(file.toFile())) {
			return readReport(zip);
		}
	}

	private static List<String> readReport(ZipFile zip) throws IOException {
		ZipEntry e = zip.getEntry(REPORT);
		if (e == null) return null;
		try (InputStream in = zip.getInputStream(e)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
		}
	}

	/** Closes a conversion got with {@code keep} false, unless it is the one the game has open. */
	public synchronized void release(Entry entry) {
		if (loaded.get(entry.file()) == entry) return;
		try {
			entry.zip().close();
		} catch (IOException ignored) {
		}
	}

	/** Same as {@link #get(String, Path, PackSource, PackMeta, VanillaAssets)} for packs without a known location. */
	/**
	 * Converts the pack now if it isn't converted yet, so that loading it later only reads the cache (for converting
	 * ahead, off the render thread). The conversion's result if it was converted now, else null.
	 */
	public synchronized Converter.@Nullable Result prepare(String packName, Path location, PackSource source, PackMeta meta, VanillaAssets vanilla)
		throws IOException {
		String base = safeName(packName);
		Options options = this.options.get();
		Path file = root.resolve(base + "-" + key(source.fingerprint() + options.key()) + ".zip");
		if (loaded.containsKey(file) || Files.isRegularFile(file)) return null;
		Converter.Result result = Converter.convert(source, meta, vanilla, options);
		write(file, location, result);
		deleteStale(base, file);
		enforceLimit();
		return result;
	}

	public Entry get(String packName, PackSource source, PackMeta meta, VanillaAssets vanilla) throws IOException {
		return get(packName, null, source, meta, vanilla);
	}

	private Entry read(Path file) {
		ZipFile zip = null;
		try {
			zip = new ZipFile(file.toFile());
			ZipEntry indexEntry = zip.getEntry(INDEX);
			if (indexEntry == null) {
				zip.close();
				return null;
			}
			JsonObject index;
			try (InputStream in = zip.getInputStream(indexEntry)) {
				index = Json.obj(Json.parse(in.readAllBytes()));
			}
			JsonObject files = Json.obj(index, "files");
			if (files == null || index.get("converter") == null || index.get("converter").getAsInt() != Converter.VERSION) {
				zip.close();
				return null;
			}
			TreeMap<String, String> map = new TreeMap<>();
			for (Map.Entry<String, JsonElement> e : files.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
			return new Entry(file, zip, Collections.unmodifiableNavigableMap(map), false, null);
		} catch (IOException | RuntimeException e) {
			if (zip != null) {
				try {
					zip.close();
				} catch (IOException ignored) {
				}
			}
			return null;
		}
	}

	private void write(Path file, Path location, Converter.Result result) throws IOException {
		Files.createDirectories(root);
		TreeMap<String, String> map = new TreeMap<>();
		List<Map.Entry<String, PackTree.Node>> generated = new ArrayList<>();
		for (Map.Entry<String, PackTree.Node> e : result.tree().nodes().entrySet()) {
			if (!safePath(e.getKey())) continue;
			if (e.getValue() instanceof PackTree.SourceRef ref) {
				map.put(e.getKey(), ref.sourcePath());
			} else {
				map.put(e.getKey(), GENERATED);
				generated.add(e);
			}
		}
		// Encoding (PNG deflate) is the expensive part: spread it over the cores, then write sequentially.
		List<byte[]> bytes = generated.parallelStream().map(e -> ((PackTree.Generated) e.getValue()).bytes()).toList();

		Path tmp = Files.createTempFile(root, file.getFileName().toString(), ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
				put(zip, SOURCE, (location != null ? location.toAbsolutePath().normalize().toString() : "").getBytes(StandardCharsets.UTF_8), false);
				JsonObject index = new JsonObject();
				index.addProperty("converter", Converter.VERSION);
				index.addProperty("from_format", result.context().meta().format().toString());
				JsonObject files = new JsonObject();
				map.forEach(files::addProperty);
				index.add("files", files);
				put(zip, INDEX, Json.GSON.toJson(index).getBytes(StandardCharsets.UTF_8), true);
				StringBuilder report = new StringBuilder();
				report.append("Converted from format ").append(result.context().meta().format()).append(" in ").append(result.nanos() / 1_000_000).append(" ms\n");
				result.context().counters().forEach((k, v) -> report.append(k).append(": ").append(v).append('\n'));
				result.context().warnings().forEach(w -> report.append("warning: ").append(w).append('\n'));
				put(zip, REPORT, report.toString().getBytes(StandardCharsets.UTF_8), true);
				for (int i = 0; i < generated.size(); i++) {
					String path = generated.get(i).getKey();
					// PNGs are compressed already: store them, deflate the rest
					put(zip, FILES + path, bytes.get(i), !path.endsWith(".png"));
				}
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private static void put(ZipOutputStream zip, String name, byte[] data, boolean deflate) throws IOException {
		ZipEntry e = new ZipEntry(name);
		if (!deflate) {
			e.setMethod(ZipEntry.STORED);
			e.setSize(data.length);
			e.setCompressedSize(data.length);
			CRC32 crc = new CRC32();
			crc.update(data);
			e.setCrc(crc.getValue());
		}
		zip.putNextEntry(e);
		zip.write(data);
		zip.closeEntry();
	}

	/** Pack-relative paths only: no absolute paths, drive letters or ".." segments (zip slip). */
	static boolean safePath(String path) {
		if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains(":")) return false;
		for (String segment : path.split("/")) {
			if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
		}
		return true;
	}

	private static void touch(Path file) {
		try {
			Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
		} catch (IOException ignored) {
		}
	}

	/** Older conversions of the same pack (same name, other key). Other packs whose name merely starts the same are kept. */
	private void deleteStale(String base, Path keep) {
		String pattern = java.util.regex.Pattern.quote(base) + "-[0-9a-f]{12}\\.zip";
		try (DirectoryStream<Path> files = Files.newDirectoryStream(root, base + "-*.zip")) {
			for (Path f : files) {
				if (!f.equals(keep) && f.getFileName().toString().matches(pattern)) delete(f);
			}
		} catch (IOException ignored) {
		}
	}

	private void delete(Path file) {
		Entry e = loaded.remove(file);
		if (e != null) {
			try {
				e.zip().close();
			} catch (IOException ignored) {
			}
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// still open somewhere (Windows): the next cleanup gets it
		}
	}

	/** Deletes the conversions used longest ago until the cache fits the limit; ones in use are kept. */
	public synchronized void enforceLimit() {
		long limit = maxBytes.getAsLong();
		List<Path> files = list();
		long total = 0;
		for (Path f : files) total += size(f);
		if (total <= limit) return;
		files.sort(Comparator.comparingLong(ConversionCache::lastUsed));
		for (Path f : files) {
			if (total <= limit) break;
			if (loaded.containsKey(f)) continue;
			long s = size(f);
			delete(f);
			if (!Files.exists(f)) total -= s;
		}
	}

	/** Deletes conversions whose pack is gone (it can never be used again), then applies the size limit. */
	public synchronized void cleanUp() {
		for (Path f : list()) {
			if (loaded.containsKey(f)) continue;
			String source = null;
			try (ZipFile zip = new ZipFile(f.toFile())) {
				ZipEntry e = zip.getEntry(SOURCE);
				if (e != null) {
					try (InputStream in = zip.getInputStream(e)) {
						source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
					}
				}
			} catch (IOException ex) {
				delete(f); // damaged
				continue;
			}
			if (source != null && !source.isEmpty() && !Files.exists(Path.of(source))) delete(f);
		}
		enforceLimit();
	}

	/** Deletes every conversion not in use; returns the bytes freed. */
	public synchronized long clear() {
		long freed = 0;
		for (Path f : list()) {
			if (loaded.containsKey(f)) continue;
			long s = size(f);
			delete(f);
			if (!Files.exists(f)) freed += s;
		}
		return freed;
	}

	/** Total size of the cache on disk. */
	public long size() {
		long total = 0;
		for (Path f : list()) total += size(f);
		return total;
	}

	private List<Path> list() {
		List<Path> files = new ArrayList<>();
		if (!Files.isDirectory(root)) return files;
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*.zip")) {
			for (Path f : stream) files.add(f);
		} catch (IOException ignored) {
		}
		return files;
	}

	private static long size(Path f) {
		try {
			return Files.size(f);
		} catch (IOException e) {
			return 0;
		}
	}

	private static long lastUsed(Path f) {
		try {
			return Files.getLastModifiedTime(f).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	static String key(String fingerprint) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest((fingerprint + "|" + Converter.VERSION).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest, 0, 6);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	static String safeName(String name) {
		String s = name.replaceAll("[^A-Za-z0-9._-]", "_");
		if (s.length() > 48) s = s.substring(0, 48);
		return s.isEmpty() ? "pack" : s;
	}
}
