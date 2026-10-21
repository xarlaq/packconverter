package dev.packconverter.mc;

import dev.packconverter.PackConverterClient;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.mixin.SoundManagerAccessor;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.resources.language.LanguageManager;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Faster pack switching (a setting, on by default). Switching packs in the pack screen reloads every resource; the
 * shaders (compiled again each time: seconds of work), fonts, sounds and languages only change if a pack that was
 * added, removed or moved has any. When none has, those keep what they have (sounds keep playing, too; their files
 * are listed again, {@link SoundFiles}). The pack list isn't read again either ({@code MinecraftMixin}). F3+T and
 * every other reload still reload everything. The loading screen stays out of the way ({@code LoadingOverlayMixin}),
 * and atlases are put together in the background ({@link AtlasUpload}).
 */
public final class FastReload {
	/** What a pack has that the skippable listeners read. */
	private record Contents(boolean shaders, boolean fonts, boolean sounds, boolean languages) {
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

	/** A pack switch from the pack screen is starting, and fast switching is on. */
	public static boolean fastSwitch() {
		return packSwitch && enabled();
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
		boolean shaders = false, fonts = false, sounds = false, languages = false;
		for (String id : changed) {
			Contents c = KNOWN.get(id);
			if (c == null) return all; // a built-in pack went away unread: reload everything
			shaders |= c.shaders();
			fonts |= c.fonts();
			sounds |= c.sounds();
			languages |= c.languages();
		}
		List<PreparableReloadListener> needed = new ArrayList<>(all.size());
		List<String> left = new ArrayList<>();
		for (PreparableReloadListener listener : all) {
			if (!shaders && listener instanceof ShaderManager) left.add("shaders");
			else if (!fonts && listener instanceof FontManager) left.add("fonts");
			else if (!sounds && listener instanceof SoundManager soundManager) {
				left.add("sounds");
				needed.add(new SoundFiles(soundManager));
			}
			else if (!languages && listener instanceof LanguageManager) left.add("languages");
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

	/**
	 * Stands in for the sound manager when the sounds are kept: its sounds stay, but the files it opens them from are
	 * listed again from the packs this reload opened. Those listed before belong to the packs the reload closes (a
	 * zip's files can't be read once it is closed), so sounds not loaded yet, and streamed ones, stayed silent until
	 * F3+T: a server pack's sounds, for one. Only the listing, nothing is decoded.
	 */
	private static final class SoundFiles extends SimplePreparableReloadListener<Map<Identifier, Resource>> {
		private final SoundManager sounds;

		private SoundFiles(SoundManager sounds) {
			this.sounds = sounds;
		}

		@Override
		protected Map<Identifier, Resource> prepare(ResourceManager manager, ProfilerFiller profiler) {
			return Sound.SOUND_LISTER.listMatchingResources(manager);
		}

		@Override
		protected void apply(Map<Identifier, Resource> files, ResourceManager manager, ProfilerFiller profiler) {
			Map<Identifier, Resource> cache = ((SoundManagerAccessor) this.sounds).packconverter$soundCache();
			cache.clear();
			cache.putAll(files);
		}
	}

	private static Contents contents(PackResources pack) {
		boolean[] shaders = {false}, fonts = {false}, sounds = {false}, languages = {false};
		try {
			for (String ns : pack.getNamespaces(PackType.CLIENT_RESOURCES)) {
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "shaders", (id, resource) -> shaders[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "post_effect", (id, resource) -> shaders[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "font", (id, resource) -> fonts[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "textures/font", (id, resource) -> fonts[0] = true);
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "sounds", (id, resource) -> sounds[0] = true);
				if (pack.getResource(PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath(ns, "sounds.json")) != null) sounds[0] = true;
				pack.listResources(PackType.CLIENT_RESOURCES, ns, "lang", (id, resource) -> languages[0] = true);
			}
		} catch (RuntimeException e) {
			PackConverterClient.LOGGER.debug("Could not list {}", pack.packId(), e);
			return new Contents(true, true, true, true);
		}
		return new Contents(shaders[0], fonts[0], sounds[0], languages[0]);
	}
}
