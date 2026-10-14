package dev.packconverter.mc;

import dev.packconverter.PackConverterClient;
import dev.packconverter.PackConverterConfig;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Faster pack switching (a setting, on by default). Switching packs in the pack screen reloads every resource; the
 * shaders (compiled again each time: seconds of work) and fonts only change if a pack that was added, removed or
 * moved has any. When none has, those two keep what they have. F3+T and every other reload still reload
 * everything. The loading screen's fade is shortened too ({@code LoadingOverlayMixin}).
 */
public final class FastReload {
	/** What a pack has that the skippable listeners read. */
	private record Contents(boolean shaders, boolean fonts) {
	}

	private static boolean packSwitch;
	/** The packs of the last reload, in order. */
	private static List<String> lastPacks = List.of();
	/** What each pack seen has, by id (packs in folders and zips are read; built-in ones only when they come or go). */
	private static final Map<String, Contents> KNOWN = new HashMap<>();
	/** What the last reload left out, for the log line. */
	private static String skipped = "";

	private FastReload() {
	}

	/** Runs the pack screen's apply of a new selection (the reload it starts may skip listeners). */
	public static void whileSwitching(Runnable apply) {
		packSwitch = true;
		try {
			apply.run();
		} finally {
			packSwitch = false;
		}
	}

	public static boolean enabled() {
		return PackConverterConfig.get().fastReload;
	}

	/** The listeners a reload of these packs needs (render thread, as the reload starts). */
	public static List<PreparableReloadListener> listeners(List<PreparableReloadListener> all, List<PackResources> packs) {
		List<String> ids = packs.stream().map(PackResources::packId).toList();
		List<String> before = lastPacks;
		lastPacks = ids;
		skipped = "";
		for (PackResources pack : packs) {
			if (pack.packId().startsWith("file/")) KNOWN.computeIfAbsent(pack.packId(), id -> contents(pack));
		}
		if (!packSwitch || !enabled() || before.isEmpty()) return all;
		Set<String> changed = changed(before, ids);
		for (PackResources pack : packs) {
			if (changed.contains(pack.packId())) KNOWN.computeIfAbsent(pack.packId(), id -> contents(pack));
		}
		boolean shaders = false, fonts = false;
		for (String id : changed) {
			Contents c = KNOWN.get(id);
			if (c == null) return all; // a built-in pack went away unread: reload everything
			shaders |= c.shaders();
			fonts |= c.fonts();
		}
		List<PreparableReloadListener> needed = new ArrayList<>(all.size());
		List<String> left = new ArrayList<>();
		for (PreparableReloadListener listener : all) {
			if (!shaders && listener instanceof ShaderManager) left.add("shaders");
			else if (!fonts && listener instanceof FontManager) left.add("fonts");
			else needed.add(listener);
		}
		skipped = String.join(", ", left);
		return needed;
	}

	/** For the log: what the last reload kept instead of reloading ("" if nothing). */
	public static String skipped() {
		return skipped;
	}

	/** Packs added, removed, or no longer in the same order relative to the others. */
	public static Set<String> changed(List<String> before, List<String> after) {
		Set<String> changed = new LinkedHashSet<>();
		Set<String> old = new HashSet<>(before), now = new HashSet<>(after);
		for (String id : before) if (!now.contains(id)) changed.add(id);
		for (String id : after) if (!old.contains(id)) changed.add(id);
		List<String> oldOrder = before.stream().filter(now::contains).toList(), newOrder = after.stream().filter(old::contains).toList();
		for (int i = 0; i < oldOrder.size(); i++) {
			if (!oldOrder.get(i).equals(newOrder.get(i))) {
				changed.add(oldOrder.get(i));
				changed.add(newOrder.get(i));
			}
		}
		return changed;
	}

	private static Contents contents(PackResources pack) {
		boolean[] shaders = {false}, fonts = {false};
		try {
			for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "shaders", (id, resource) -> shaders[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "post_effect", (id, resource) -> shaders[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "font", (id, resource) -> fonts[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "textures/font", (id, resource) -> fonts[0] = true);
			}
		} catch (RuntimeException e) {
			PackConverterClient.LOGGER.debug("Could not list {}", pack.packId(), e);
			return new Contents(true, true);
		}
		return new Contents(shaders[0], fonts[0]);
	}
}
