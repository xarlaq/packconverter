package dev.packconverter.core;

import com.google.gson.JsonElement;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.io.Json;
import dev.packconverter.core.source.PackSource;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The pack being converted, as a virtual file tree. Every path ('/'-separated, relative to the pack root)
 * maps to either an untouched file of the source pack ({@link SourceRef}, served straight from the
 * original zip or folder, never copied) or new content produced by a step ({@link Generated}).
 * Parsed JSON and decoded images are cached so steps can share them; a step that changes one writes it back
 * with {@link #putJson}/{@link #putImage}.
 */
public final class PackTree {
	private final PackSource source;
	private final Map<String, Node> nodes = new HashMap<>();

	public sealed interface Node permits SourceRef, Generated {
	}

	/** Unchanged file, still at {@code sourcePath} in the source pack. */
	public record SourceRef(String sourcePath) implements Node {
	}

	/** New content: exactly one of bytes/json/image is the authoritative form. */
	public static final class Generated implements Node {
		private byte[] bytes;
		private JsonElement json;
		private BufferedImage image;

		Generated(byte[] bytes, JsonElement json, BufferedImage image) {
			this.bytes = bytes;
			this.json = json;
			this.image = image;
		}

		public byte[] bytes() {
			if (bytes == null) {
				bytes = json != null ? Json.toBytes(json) : Img.encodePng(image);
			}
			return bytes;
		}
	}

	// Parsed views of SourceRef nodes (not modified unless written back).
	private final Map<String, JsonElement> jsonCache = new HashMap<>();
	private final Map<String, BufferedImage> imageCache = new java.util.concurrent.ConcurrentHashMap<>();

	public PackTree(PackSource source) {
		this.source = source;
		for (String path : source.paths()) {
			String normalized = normalize(path);
			if (!normalized.isEmpty() && !normalized.endsWith("/")) {
				nodes.putIfAbsent(normalized, new SourceRef(path));
			}
		}
	}

	public static String normalize(String path) {
		String p = path.replace('\\', '/');
		while (p.startsWith("/") || p.startsWith("./")) p = p.substring(p.startsWith("/") ? 1 : 2);
		return p;
	}

	public PackSource source() {
		return source;
	}

	public boolean has(String path) {
		return nodes.containsKey(path);
	}

	public Node node(String path) {
		return nodes.get(path);
	}

	/** Snapshot of all paths, sorted (deterministic step behaviour). */
	public List<String> paths() {
		return new ArrayList<>(new TreeSet<>(nodes.keySet()));
	}

	/** Snapshot of paths starting with {@code prefix}. */
	public List<String> paths(String prefix) {
		List<String> result = new ArrayList<>();
		for (String p : nodes.keySet()) if (p.startsWith(prefix)) result.add(p);
		result.sort(null);
		return result;
	}

	public List<String> paths(Predicate<String> filter) {
		List<String> result = new ArrayList<>();
		for (String p : nodes.keySet()) if (filter.test(p)) result.add(p);
		result.sort(null);
		return result;
	}

	public int size() {
		return nodes.size();
	}

	public byte[] bytes(String path) {
		Node node = nodes.get(path);
		if (node == null) return null;
		if (node instanceof Generated g) return g.bytes();
		try {
			return source.read(((SourceRef) node).sourcePath());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Parsed JSON (lenient), or null if missing or not JSON. The returned tree may be mutated, then {@link #putJson}. */
	public JsonElement json(String path) {
		Node node = nodes.get(path);
		if (node == null) return null;
		if (node instanceof Generated g) {
			if (g.json == null && g.image == null) g.json = Json.parse(g.bytes);
			return g.json;
		}
		return jsonCache.computeIfAbsent(path, p -> Json.parse(bytes(p)));
	}

	/** Decoded ARGB image, or null if missing or undecodable. Treat as read-only unless written back with {@link #putImage}. */
	public BufferedImage image(String path) {
		Node node = nodes.get(path);
		if (node == null) return null;
		if (node instanceof Generated g) {
			if (g.image == null && g.json == null) g.image = Img.decode(g.bytes);
			return g.image;
		}
		BufferedImage cached = imageCache.get(path);
		if (cached == null) {
			cached = Img.decode(bytes(path));
			if (cached != null) imageCache.put(path, cached);
		}
		return cached;
	}

	/** Width and height of a PNG without decoding it when possible, or null. */
	public int[] imageSize(String path) {
		Node node = nodes.get(path);
		if (node == null) return null;
		if (node instanceof Generated g && g.image != null) return new int[]{g.image.getWidth(), g.image.getHeight()};
		BufferedImage cached = imageCache.get(path);
		if (cached != null) return new int[]{cached.getWidth(), cached.getHeight()};
		int[] size;
		if (node instanceof SourceRef ref) {
			try (java.io.InputStream in = source.open(ref.sourcePath())) {
				size = Img.pngSize(in.readNBytes(24)); // the header only, not the whole file
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else {
			size = Img.pngSize(bytes(path));
		}
		if (size != null) return size;
		BufferedImage img = image(path);
		return img == null ? null : new int[]{img.getWidth(), img.getHeight()};
	}

	/**
	 * Decodes the given images on all cores ahead of a step that reads them (decoding is most of the conversion
	 * time). Only untouched source files: generated images are already in memory.
	 */
	public void prefetchImages(java.util.Collection<String> paths) {
		List<String> todo = new ArrayList<>();
		for (String p : paths) {
			if (nodes.get(p) instanceof SourceRef && !imageCache.containsKey(p) && p.endsWith(".png")) todo.add(p);
		}
		if (todo.size() < 2) return;
		Map<String, String> sources = new HashMap<>();
		for (String p : todo) sources.put(p, ((SourceRef) nodes.get(p)).sourcePath());
		todo.parallelStream().forEach(p -> {
			try {
				BufferedImage img = Img.decode(source.read(sources.get(p)));
				if (img != null) imageCache.putIfAbsent(p, img);
			} catch (IOException ignored) {
				// read again (and reported) by the step itself
			}
		});
	}

	public void putBytes(String path, byte[] data) {
		evict(path);
		nodes.put(path, new Generated(data, null, null));
	}

	public void putJson(String path, JsonElement json) {
		evict(path);
		nodes.put(path, new Generated(null, json, null));
	}

	public void putImage(String path, BufferedImage image) {
		evict(path);
		nodes.put(path, new Generated(null, null, image));
	}

	public void putString(String path, String text) {
		putBytes(path, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public void remove(String path) {
		evict(path);
		nodes.remove(path);
	}

	/** Copies a node (sharing the source reference or content). */
	public boolean copy(String from, String to) {
		Node node = nodes.get(from);
		if (node == null || from.equals(to)) return false;
		evict(to);
		if (node instanceof SourceRef ref) {
			nodes.put(to, ref);
			JsonElement json = jsonCache.get(from);
			if (json != null) jsonCache.put(to, json.deepCopy());
			BufferedImage image = imageCache.get(from);
			if (image != null) imageCache.put(to, image);
		} else {
			Generated g = (Generated) node;
			nodes.put(to, new Generated(g.bytes, g.json != null ? g.json.deepCopy() : null, g.image));
		}
		return true;
	}

	public boolean move(String from, String to) {
		if (!copy(from, to)) return false;
		remove(from);
		return true;
	}

	/**
	 * Applies many renames at once (so cyclic renames like grass -> grass_block, tallgrass -> grass work).
	 * When {@code overwrite} is false, a rename whose target already exists (and is not itself moving away) is
	 * skipped and the old file stays where it was.
	 */
	public int renameAll(Map<String, String> renames, boolean overwrite) {
		Map<String, Node> moving = new HashMap<>();
		Map<String, JsonElement> movingJson = new HashMap<>();
		Map<String, BufferedImage> movingImages = new HashMap<>();
		Set<String> leaving = renames.keySet();
		for (Map.Entry<String, String> e : renames.entrySet()) {
			String from = e.getKey(), to = e.getValue();
			if (!nodes.containsKey(from) || from.equals(to)) continue;
			if (!overwrite && nodes.containsKey(to) && !leaving.contains(to)) continue;
			moving.put(from, null);
		}
		for (String from : moving.keySet()) {
			moving.put(from, nodes.remove(from));
			JsonElement json = jsonCache.remove(from);
			if (json != null) movingJson.put(from, json);
			BufferedImage image = imageCache.remove(from);
			if (image != null) movingImages.put(from, image);
		}
		for (Map.Entry<String, Node> e : moving.entrySet()) {
			String to = renames.get(e.getKey());
			evict(to);
			nodes.put(to, e.getValue());
			if (movingJson.containsKey(e.getKey())) jsonCache.put(to, movingJson.get(e.getKey()));
			if (movingImages.containsKey(e.getKey())) imageCache.put(to, movingImages.get(e.getKey()));
		}
		return moving.size();
	}

	private void evict(String path) {
		jsonCache.remove(path);
		imageCache.remove(path);
	}

	/** All nodes, for the final result. */
	public Map<String, Node> nodes() {
		return nodes;
	}
}
