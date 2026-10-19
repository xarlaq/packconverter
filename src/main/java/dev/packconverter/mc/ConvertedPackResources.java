package dev.packconverter.mc;

import dev.packconverter.core.ConversionCache;
import dev.packconverter.core.source.PackSource;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;

/**
 * Serves a converted pack: unchanged files straight from the original zip or folder, generated ones from the
 * conversion cache. Listing uses the sorted index (prefix range), so it costs the same as a normal pack.
 */
public final class ConvertedPackResources extends AbstractPackResources implements PackResources {
	private final PackSource source;
	private final ConversionCache.Entry entry;
	private final NavigableMap<String, String> files;

	public ConvertedPackResources(PackLocationInfo location, PackSource source, ConversionCache.Entry entry) {
		super(location);
		this.source = source;
		this.entry = entry;
		this.files = entry.files();
	}

	private @Nullable IoSupplier<InputStream> supplier(String path) {
		String sourcePath = files.get(path);
		if (sourcePath == null) return null;
		if (sourcePath.equals(ConversionCache.GENERATED)) return () -> entry.openGenerated(path);
		return () -> source.open(sourcePath);
	}

	@Override
	public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
		return supplier(String.join("/", path));
	}

	@Override
	public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
		return supplier(type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath());
	}

	@Override
	public void listResources(PackType type, String namespace, String directory, ResourceOutput output) {
		String root = type.getDirectory() + "/" + namespace + "/";
		String prefix = root + directory + "/";
		for (Map.Entry<String, String> e : files.subMap(prefix, true, prefix + Character.MAX_VALUE, false).entrySet()) {
			Identifier id = Identifier.tryBuild(namespace, e.getKey().substring(root.length()));
			if (id != null) output.accept(id, supplier(e.getKey()));
		}
	}

	@Override
	public Set<String> getNamespaces(PackType type) {
		String root = type.getDirectory() + "/";
		Set<String> namespaces = new HashSet<>();
		for (String path : files.subMap(root, true, root + Character.MAX_VALUE, false).keySet()) {
			int slash = path.indexOf('/', root.length());
			if (slash < 0) continue;
			String ns = path.substring(root.length(), slash);
			if (Identifier.isValidNamespace(ns)) namespaces.add(ns);
		}
		return namespaces;
	}

	@Override
	public void close() {
		try {
			source.close();
		} catch (IOException ignored) {
		}
	}
}
