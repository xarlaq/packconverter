package dev.packconverter.core.source;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/** Read access to the files of a resource pack (zip or folder). Paths are the pack's own entry names. */
public interface PackSource extends Closeable {
	Collection<String> paths();

	InputStream open(String path) throws IOException;

	default byte[] read(String path) throws IOException {
		try (InputStream in = open(path)) {
			return in.readAllBytes();
		}
	}

	/** Identity used for cache keys: changes whenever the pack content may have changed. */
	String fingerprint() throws IOException;

	static PackSource open(Path path) throws IOException {
		return Files.isDirectory(path) ? new DirPackSource(path) : new ZipPackSource(path);
	}

	@Override
	void close() throws IOException;
}
