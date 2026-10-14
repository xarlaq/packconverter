package dev.packconverter.core.source;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.function.Predicate;

/** Only some files of another pack (closing it is left to its owner). */
public final class FilteredPackSource implements PackSource {
	private final PackSource delegate;
	private final Collection<String> paths;

	public FilteredPackSource(PackSource delegate, Predicate<String> keep) {
		this.delegate = delegate;
		this.paths = delegate.paths().stream().filter(keep).toList();
	}

	@Override
	public Collection<String> paths() {
		return this.paths;
	}

	@Override
	public InputStream open(String path) throws IOException {
		return this.delegate.open(path);
	}

	@Override
	public String fingerprint() throws IOException {
		return this.delegate.fingerprint();
	}

	@Override
	public void close() {
	}
}
