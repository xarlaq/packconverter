package dev.packconverter.core;

import dev.packconverter.core.source.PackSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a converted pack as a normal standalone zip, for clients without this mod (or to share). Reuses the
 * cached conversion when there is one; the zip holds every file, untouched ones copied from the original.
 */
public final class Exporter {
	public record Result(Path file, long bytes, int files) {
	}

	private Exporter() {
	}

	/**
	 * Exports with {@code options}: the same as the game's go through the cache; others (a netherite colour picked
	 * for this export) are converted on their own, so the game's cached conversion stays as it is.
	 */
	public static Result export(ConversionCache cache, String name, Path location, PackSource source, PackMeta meta, VanillaAssets vanilla,
		Options options, Path out) throws IOException {
		if (!options.equals(cache.options())) return write(Converter.convert(source, meta, vanilla, options).tree(), source, out);
		ConversionCache.Entry entry = cache.get(name, location, source, meta, vanilla, false);
		try {
			return write(entry, source, out);
		} finally {
			cache.release(entry);
		}
	}

	private static Result write(ConversionCache.Entry entry, PackSource source, Path out) throws IOException {
		return write(out, entry.files(), path -> {
			String from = entry.files().get(path);
			return from.equals(ConversionCache.GENERATED) ? entry.openGenerated(path) : source.open(from);
		});
	}

	private static Result write(PackTree tree, PackSource source, Path out) throws IOException {
		Map<String, PackTree.Node> nodes = new TreeMap<>(tree.nodes());
		nodes.keySet().removeIf(path -> !ConversionCache.safePath(path));
		return write(out, nodes, path -> nodes.get(path) instanceof PackTree.SourceRef ref
			? source.open(ref.sourcePath()) : new java.io.ByteArrayInputStream(((PackTree.Generated) nodes.get(path)).bytes()));
	}

	private interface Opener {
		InputStream open(String path) throws IOException;
	}

	private static Result write(Path out, Map<String, ?> files, Opener opener) throws IOException {
		Files.createDirectories(out.getParent());
		Path tmp = Files.createTempFile(out.getParent(), out.getFileName().toString(), ".tmp");
		int count = 0;
		try {
			try (OutputStream os = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(os)) {
				byte[] buffer = new byte[1 << 16];
				for (String path : files.keySet()) {
					zip.putNextEntry(new ZipEntry(path));
					try (InputStream in = opener.open(path)) {
						int n;
						while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
					}
					zip.closeEntry();
					count++;
				}
			}
			Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp);
		}
		return new Result(out, Files.size(out), count);
	}
}
