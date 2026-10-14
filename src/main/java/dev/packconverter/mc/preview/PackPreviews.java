package dev.packconverter.mc.preview;

import com.mojang.blaze3d.platform.NativeImage;
import dev.packconverter.PackConverterClient;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.core.HudPreview;
import dev.packconverter.core.HudPreview.Piece;
import dev.packconverter.core.HudPreview.Region;
import dev.packconverter.core.io.Img;
import dev.packconverter.core.source.PackSource;
import dev.packconverter.mc.LegacyPacks;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Previews for the resource pack screen ({@link PackPreview}). Read on a background thread the first time a pack is
 * hovered (only the pack last asked for: moving over a list doesn't queue every pack), uploaded on the render
 * thread, a few kept, all dropped when the pack screen closes. Nothing happens outside that screen.
 */
public final class PackPreviews {
	private static final int KEEP = 8;
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Pack Converter preview");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});

	/** Read, not yet uploaded. */
	private record Loaded(NativeImage image, Map<Piece, Region> regions, Set<Piece> fromPack) {
	}

	private static final Map<String, Loaded> READY = new ConcurrentHashMap<>();
	/** Packs with nothing to preview (no HUD or item textures of their own, or unreadable). */
	private static final Set<String> NONE = ConcurrentHashMap.newKeySet();
	private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
	/** Uploaded, most recently shown last (render thread only). */
	private static final LinkedHashMap<String, PackPreview> SHOWN = new LinkedHashMap<>(KEEP, 0.75f, true);
	private static volatile @Nullable String wanted;
	/** Bumped by {@link #clear}: reads started before are thrown away. */
	private static volatile int generation;
	private static int textures;

	private PackPreviews() {
	}

	public static void register() {
		PictureInPictureRendererRegistry.register(context -> new ArmorPreviewRenderer());
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof PackSelectionScreen) ScreenEvents.remove(screen).register(s -> clear());
		});
	}

	/** The preview of a pack in the resource pack screen, or null (none, switched off, or still being read). Render thread. */
	public static @Nullable PackPreview get(String packId) {
		if (!PackConverterConfig.get().packPreviews) return null;
		PackPreview shown = SHOWN.get(packId);
		if (shown != null) return shown;
		if (NONE.contains(packId)) return null;
		Loaded loaded = READY.remove(packId);
		if (loaded != null) return upload(packId, loaded);
		wanted = packId;
		if (LOADING.add(packId)) {
			// the pack's own file, not one made from its id (Lunar names packs in its folders by file name alone)
			Pack pack = Minecraft.getInstance().getResourcePackRepository().getPack(packId);
			Path path = pack == null ? null : LegacyPacks.pathOf(pack);
			if (path == null) {
				LOADING.remove(packId);
				NONE.add(packId);
				return null;
			}
			int gen = generation;
			LOADER.execute(() -> load(packId, path, gen));
		}
		return null;
	}

	private static void load(String packId, Path path, int gen) {
		try {
			if (!packId.equals(wanted)) return; // the mouse moved on before this started
			Loaded loaded = read(path);
			if (loaded == null) NONE.add(packId);
			else if (gen != generation || READY.putIfAbsent(packId, loaded) != null) loaded.image().close();
		} catch (Exception | LinkageError e) {
			NONE.add(packId);
			PackConverterClient.LOGGER.debug("No preview for {}", packId, e);
		} finally {
			LOADING.remove(packId);
		}
	}

	private static @Nullable Loaded read(Path path) throws Exception {
		if (!Files.exists(path)) return null;
		HudPreview.Sheet sheet;
		try (PackSource pack = PackSource.open(path)) {
			sheet = HudPreview.read(pack, LegacyPacks.VANILLA, PackConverterConfig.get().conversionOptions());
		}
		if (sheet == null) return null;
		int w = sheet.image().getWidth(), h = sheet.image().getHeight();
		int[] px = Img.pixels(sheet.image());
		NativeImage image = new NativeImage(w, h, false);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) image.setPixel(x, y, px[y * w + x]);
		}
		return new Loaded(image, sheet.regions(), sheet.fromPack());
	}

	private static PackPreview upload(String packId, Loaded loaded) {
		Identifier id = Identifier.fromNamespaceAndPath("packconverter", "preview/" + textures++);
		int w = loaded.image().getWidth(), h = loaded.image().getHeight();
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Pack preview " + packId, loaded.image()));
		PackPreview preview = new PackPreview(id, w, h, loaded.regions(), loaded.fromPack());
		SHOWN.put(packId, preview);
		Iterator<PackPreview> oldest = SHOWN.values().iterator();
		while (SHOWN.size() > KEEP) {
			oldest.next().release();
			oldest.remove();
		}
		return preview;
	}

	/** Previews uploaded at the moment (for tests). */
	public static int uploaded() {
		return SHOWN.size();
	}

	/** Drops every preview (the pack screen closed). Render thread. */
	public static void clear() {
		generation++;
		wanted = null;
		SHOWN.values().forEach(PackPreview::release);
		SHOWN.clear();
		READY.values().forEach(l -> l.image().close());
		READY.clear();
		NONE.clear();
	}
}
