package dev.packconverter.core.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Pack in a zip file. Thread-safe for reads (ZipFile is). */
public final class ZipPackSource implements PackSource {
	private final Path path;
	/** Opened again if used after close(), as vanilla's zip packs do (some clients, Lunar, read a pack after closing it). */
	private ZipFile zip;
	private final List<String> paths;

	public ZipPackSource(Path path) throws IOException {
		this.path = path;
		this.zip = new ZipFile(path.toFile());
		List<String> list = new ArrayList<>();
		for (var entries = zip.entries(); entries.hasMoreElements(); ) {
			ZipEntry entry = entries.nextElement();
			if (!entry.isDirectory()) list.add(entry.getName());
		}
		this.paths = Collections.unmodifiableList(list);
	}

	@Override
	public Collection<String> paths() {
		return paths;
	}

	@Override
	public InputStream open(String name) throws IOException {
		try {
			return this.open(this.zip(), name);
		} catch (IllegalStateException closed) {
			// closed by another thread in between: open it again
			return this.open(this.zip(), name);
		}
	}

	private InputStream open(ZipFile zip, String name) throws IOException {
		ZipEntry entry = zip.getEntry(name);
		if (entry == null) throw new java.io.FileNotFoundException(name + " in " + path);
		return zip.getInputStream(entry);
	}

	private synchronized ZipFile zip() throws IOException {
		if (this.zip == null) this.zip = new ZipFile(this.path.toFile());
		return this.zip;
	}

	@Override
	public String fingerprint() throws IOException {
		BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
		return "zip:" + path.toAbsolutePath().normalize() + ":" + attrs.size() + ":" + attrs.lastModifiedTime().toMillis();
	}

	@Override
	public synchronized void close() throws IOException {
		if (this.zip != null) {
			this.zip.close();
			this.zip = null;
		}
	}

	@Override
	public String toString() {
		return path.toString();
	}
}
