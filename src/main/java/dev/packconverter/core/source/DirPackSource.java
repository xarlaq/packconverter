package dev.packconverter.core.source;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/** Pack in a folder. The fingerprint hashes every file's path, size and modification time. */
public final class DirPackSource implements PackSource {
	private final Path root;
	private final List<String> paths = new ArrayList<>();
	private final List<long[]> stats = new ArrayList<>();

	public DirPackSource(Path root) throws IOException {
		this.root = root;
		Files.walkFileTree(root, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				if (attrs.isRegularFile()) {
					paths.add(root.relativize(file).toString().replace('\\', '/'));
					stats.add(new long[]{attrs.size(), attrs.lastModifiedTime().toMillis()});
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public Collection<String> paths() {
		return paths;
	}

	@Override
	public InputStream open(String path) throws IOException {
		return Files.newInputStream(root.resolve(path));
	}

	@Override
	public String fingerprint() {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			for (int i = 0; i < paths.size(); i++) {
				md.update(paths.get(i).getBytes(StandardCharsets.UTF_8));
				md.update((":" + stats.get(i)[0] + ":" + stats.get(i)[1] + "\n").getBytes(StandardCharsets.UTF_8));
			}
			return "dir:" + root.toAbsolutePath().normalize() + ":" + HexFormat.of().formatHex(md.digest());
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void close() {
	}

	@Override
	public String toString() {
		return root.toString();
	}
}
