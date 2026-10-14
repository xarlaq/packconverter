package dev.packconverter.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-conversion state shared by the steps: the pack's declared format and a report of what happened. */
public final class Context {
	private final PackMeta meta;
	private final VanillaAssets.Cached vanilla;
	private final Options options;
	private final List<String> warnings = new ArrayList<>();
	private final Map<String, Integer> counters = new LinkedHashMap<>();
	private final Map<String, Long> timings = new LinkedHashMap<>();

	public Context(PackMeta meta, VanillaAssets vanilla) {
		this(meta, vanilla, Options.PLAIN);
	}

	public Context(PackMeta meta, VanillaAssets vanilla, Options options) {
		this.meta = meta;
		this.options = options;
		this.vanilla = vanilla instanceof VanillaAssets.Cached c ? c : new VanillaAssets.Cached(vanilla);
	}

	/** The target version's own resources (26.3). */
	public VanillaAssets.Cached vanilla() {
		return vanilla;
	}

	public PackMeta meta() {
		return meta;
	}

	/** The player's choices that change the output (netherite from diamond). */
	public Options options() {
		return options;
	}

	/** True if the pack was made for a format older than {@code format}. */
	public boolean before(int format) {
		return meta.format().major() < format;
	}

	public void warn(String message) {
		if (warnings.size() < 500) warnings.add(message);
	}

	public void count(String what, int n) {
		if (n != 0) counters.merge(what, n, Integer::sum);
	}

	void time(String step, long nanos) {
		timings.merge(step, nanos, Long::sum);
	}

	public List<String> warnings() {
		return warnings;
	}

	public Map<String, Integer> counters() {
		return counters;
	}

	public Map<String, Long> timings() {
		return timings;
	}
}
