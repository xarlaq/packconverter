package dev.packconverter.core.steps;

import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Refs;
import dev.packconverter.core.Step;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 1.11 made resource names strictly lowercase without spaces. Older packs (and the folders players unzipped
 * on Windows) often have "Diamond_Sword.png" or "my sword.png"; rename them and every reference to them.
 */
public final class LegacyNamesStep implements Step {
	@Override
	public int format() {
		return 3;
	}

	@Override
	public String name() {
		return "1.11 lowercase names";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		Map<String, String> renames = new HashMap<>();
		for (String path : tree.paths("assets/")) {
			String fixed = fix(path);
			if (!fixed.equals(path)) renames.put(path, fixed);
		}
		ctx.count("renamed files", tree.renameAll(renames, false));
		ctx.count("files with updated references", Refs.rewrite(tree, (kind, ns, path) -> {
			String fixed = fix(path);
			return fixed.equals(path) ? null : fixed;
		}));
	}

	/** Lowercase; anything else a resource id can't contain ([a-z0-9/._-]) becomes '_'. */
	private static String fix(String path) {
		String lower = path.toLowerCase(Locale.ROOT);
		StringBuilder sb = null;
		for (int i = 0; i < lower.length(); i++) {
			char c = lower.charAt(i);
			boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-';
			if (!ok) {
				if (sb == null) sb = new StringBuilder(lower);
				sb.setCharAt(i, '_');
			}
		}
		return sb == null ? lower : sb.toString();
	}
}
