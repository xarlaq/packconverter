package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackMeta;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * First step for every converted pack: applies the overlays meant for the newest version the pack declares
 * (their files replace the base pack's), drops every overlay folder, and removes OS junk files.
 */
public final class PrepareStep implements Step {
	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public String name() {
		return "prepare";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		List<PackMeta.Overlay> overlays = ctx.meta().overlays();
		if (!overlays.isEmpty()) {
			// later entries win, like the game's own overlay stack
			Map<String, String> sourceForTarget = new HashMap<>();
			for (PackMeta.Overlay overlay : overlays) {
				if (!overlay.matches(ctx.meta().maxFormat())) continue;
				String prefix = overlay.directory() + "/";
				for (String path : tree.paths(prefix)) {
					if (path.startsWith(prefix + "assets/")) sourceForTarget.put(path.substring(prefix.length()), path);
				}
			}
			for (Map.Entry<String, String> e : sourceForTarget.entrySet()) tree.copy(e.getValue(), e.getKey());
			ctx.count("overlay files applied", sourceForTarget.size());
			for (PackMeta.Overlay overlay : overlays) {
				for (String path : tree.paths(overlay.directory() + "/")) tree.remove(path);
			}
		}
		for (String path : tree.paths()) {
			String name = path.substring(path.lastIndexOf('/') + 1);
			if (path.startsWith("__MACOSX/") || name.equals(".DS_Store") || name.equalsIgnoreCase("Thumbs.db")
				|| name.equalsIgnoreCase("desktop.ini") || name.startsWith("._")) {
				tree.remove(path);
			}
		}
	}
}
