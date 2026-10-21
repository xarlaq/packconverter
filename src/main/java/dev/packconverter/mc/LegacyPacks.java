package dev.packconverter.mc;

import com.google.common.collect.MapMaker;
import dev.packconverter.PackConverterClient;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.core.ConversionCache;
import dev.packconverter.core.Converter;
import dev.packconverter.core.PackFormats;
import dev.packconverter.core.PackMeta;
import dev.packconverter.core.VanillaAssets;
import dev.packconverter.core.source.PackSource;
import dev.packconverter.mixin.FileResourcesSupplierAccessor;
import dev.packconverter.mixin.PackAccessor;
import dev.packconverter.mixin.PathResourcesSupplierAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The link between the game's pack system and the converter. When the game finds a resource pack made for an
 * older format, {@link #inspect} reports it as compatible and remembers it; when the game opens it,
 * {@link #open} serves the converted version (converting on first use, cached afterwards).
 */
public final class LegacyPacks {
	private record Info(Path path) {
	}

	private static final ThreadLocal<Boolean> LOADING = ThreadLocal.withInitial(() -> false);

	/** Metadata instances we handed out for old packs (identity, weak: dropped with their Pack). */
	private static final Map<Pack.Metadata, Info> OLD_PACKS = new MapMaker().weakKeys().makeMap();
	/** Old packs by id, for the pack screen's tooltips; their tooltip lines once read (off the render thread). */
	private static final Map<String, Info> OLD_BY_ID = new ConcurrentHashMap<>();
	private static final Map<String, List<Component>> DESCRIPTIONS = new ConcurrentHashMap<>();
	private static final Set<String> DESCRIBING = ConcurrentHashMap.newKeySet();
	private static final ExecutorService INFO = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Pack Converter info");
		t.setDaemon(true);
		return t;
	});
	/** Converting ahead ({@link #prepare}): one pack at a time, by file. */
	private static final ExecutorService PREPARE = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Pack Converter converting ahead");
		t.setDaemon(true);
		return t;
	});
	private static final Map<Path, CompletableFuture<Void>> PREPARING = new ConcurrentHashMap<>();
	private static final Pattern CONVERTED_IN = Pattern.compile("in (\\d+) ms");
	private static ConversionCache cache;
	/** The game's own assets, straight from the client jar on the classpath. */
	public static final VanillaAssets VANILLA = path -> {
		try (InputStream in = LegacyPacks.class.getClassLoader().getResourceAsStream(path)) {
			return in == null ? null : in.readAllBytes();
		} catch (java.io.IOException e) {
			return null;
		}
	};

	private LegacyPacks() {
	}

	public static synchronized ConversionCache cache() {
		if (cache == null) {
			cache = new ConversionCache(FabricLoader.getInstance().getGameDir().resolve("packconverter").resolve("cache"),
				() -> PackConverterConfig.get().cacheLimitBytes(), () -> PackConverterConfig.get().conversionOptions());
		}
		return cache;
	}

	/**
	 * Called for every client resource pack the game reads; returns replacement metadata for old packs. Runs on
	 * every pack scan (each time the pack screen opens), so it reads nothing: the game has just parsed
	 * pack.mcmeta and found the pack too old, which is all that's needed until the pack is loaded.
	 */
	public static Pack.@Nullable Metadata inspect(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata) {
		if (metadata.compatibility() != PackCompatibility.TOO_OLD || !PackConverterConfig.get().autoConvert) return null;
		Path path = pathOf(resources);
		if (path == null) return null;
		Pack.Metadata converted = new Pack.Metadata(metadata.description(), PackCompatibility.COMPATIBLE, metadata.requestedFeatures(), List.of());
		OLD_PACKS.put(converted, new Info(path));
		OLD_BY_ID.put(location.id(), new Info(path));
		DESCRIPTIONS.remove(location.id());
		return converted;
	}

	/**
	 * Tooltip lines for an old pack: what it was made for, what its conversion did (if it has been converted), its
	 * warnings. Null while they're being read (on another thread, the first time) and for packs that aren't old.
	 */
	public static @Nullable List<Component> describe(String packId) {
		Info info = OLD_BY_ID.get(packId);
		if (info == null) return null;
		List<Component> lines = DESCRIPTIONS.get(packId);
		if (lines != null) return lines;
		if (DESCRIBING.add(packId)) {
			INFO.execute(() -> {
				try {
					DESCRIPTIONS.put(packId, describe(info));
				} finally {
					DESCRIBING.remove(packId);
				}
			});
		}
		return null;
	}

	private static List<Component> describe(Info info) {
		List<Component> lines = new ArrayList<>();
		try (PackSource source = PackSource.open(info.path())) {
			PackMeta meta = Converter.readMeta(source);
			if (meta == null) return List.of();
			lines.add(Component.translatable("packconverter.info.made_for", PackFormats.describe(meta.format())).withStyle(ChatFormatting.GOLD));
			List<String> report = cache().report(info.path().getFileName().toString(), source);
			if (report == null || report.isEmpty()) {
				lines.add(Component.translatable("packconverter.info.not_yet").withStyle(ChatFormatting.GRAY));
				return lines;
			}
			Matcher ms = CONVERTED_IN.matcher(report.getFirst());
			lines.add(Component.translatable("packconverter.info.converted", ms.find() ? ms.group(1) : "?").withStyle(ChatFormatting.GREEN));
			List<String> counters = report.stream().skip(1).filter(l -> !l.startsWith("warning: ")).toList();
			List<String> warnings = report.stream().filter(l -> l.startsWith("warning: ")).map(l -> l.substring("warning: ".length())).toList();
			counters.stream().limit(8).forEach(c -> lines.add(Component.literal("  " + c).withStyle(ChatFormatting.GRAY)));
			if (counters.size() > 8) lines.add(Component.translatable("packconverter.info.more", counters.size() - 8).withStyle(ChatFormatting.DARK_GRAY));
			warnings.stream().limit(3).forEach(w -> lines.add(Component.literal("  " + w).withStyle(ChatFormatting.YELLOW)));
			if (warnings.size() > 3) lines.add(Component.translatable("packconverter.info.more", warnings.size() - 3).withStyle(ChatFormatting.DARK_GRAY));
		} catch (Exception e) {
			PackConverterClient.LOGGER.debug("Could not describe {}", info.path(), e);
		}
		return lines;
	}

	/** Runs the game's pack loading; old packs opened meanwhile (on this thread) come out converted. */
	public static <T> T whileLoading(java.util.function.Supplier<T> load) {
		boolean outer = !LOADING.get();
		LOADING.set(true);
		try {
			return load.get();
		} finally {
			if (outer) LOADING.remove();
		}
	}

	/**
	 * Converts an old pack now, in the background, if it hasn't been yet: applying it later then only reads the cache,
	 * instead of converting on the render thread as the switch starts (a long freeze for a big pack). For a pack picked
	 * at random, or moved to Selected in the pack screen. Done at once for packs that need nothing.
	 */
	public static CompletableFuture<Void> prepare(String packId) {
		Info info = OLD_BY_ID.get(packId);
		if (info == null || !PackConverterConfig.get().autoConvert) return CompletableFuture.completedFuture(null);
		Path path = info.path();
		CompletableFuture<Void> future = PREPARING.computeIfAbsent(path, p -> CompletableFuture.runAsync(() -> {
			try (PackSource source = PackSource.open(p)) {
				PackMeta meta = Converter.readMeta(source);
				if (meta == null || !Converter.needsConversion(meta)) return;
				Converter.Result result = cache().prepare(p.getFileName().toString(), p, source, meta, VANILLA);
				if (result != null) {
					DESCRIPTIONS.remove(packId);
					PackConverterClient.LOGGER.info("Converted {} ahead (made for pack format {}) in {} ms: {}", packId, meta.format(),
						result.nanos() / 1_000_000, result.context().counters());
				}
			} catch (Exception e) {
				PackConverterClient.LOGGER.warn("Could not convert {} ahead, it will be when applied", packId, e);
			}
		}, PREPARE));
		future.whenComplete((v, t) -> PREPARING.remove(path, future));
		return future;
	}

	/**
	 * Shows a change of the conversion settings (netherite, the knockback sword, their colours) at once: the old packs
	 * in use are converted again in the background, then put in again the way a pack switch is (no loading screen,
	 * sounds and the like kept). Nothing happens when no pack in use is old.
	 */
	public static void applyOptions() {
		Minecraft minecraft = Minecraft.getInstance();
		List<String> old = minecraft.getResourcePackRepository().getSelectedIds().stream().filter(OLD_BY_ID::containsKey).toList();
		if (old.isEmpty()) return;
		CompletableFuture.allOf(old.stream().map(LegacyPacks::prepare).toArray(CompletableFuture[]::new))
			.whenComplete((v, t) -> minecraft.execute(() -> FastReload.whileSwitching(minecraft::reloadResourcePacks)));
	}

	/** The converted pack, or null to let the game open the pack as it is. */
	public static @Nullable PackResources open(PackLocationInfo location, Pack.Metadata metadata) {
		if (!LOADING.get()) return null;
		Info info = OLD_PACKS.get(metadata);
		if (info == null) return null;
		PackSource source = null;
		try {
			source = PackSource.open(info.path());
			PackMeta meta = Converter.readMeta(source);
			if (meta == null || !Converter.needsConversion(meta)) {
				source.close();
				return null;
			}
			ConversionCache.Entry entry = cache().get(info.path().getFileName().toString(), info.path(), source, meta, VANILLA);
			if (entry.fresh()) {
				DESCRIPTIONS.remove(location.id());
				Converter.Result result = entry.result();
				PackConverterClient.LOGGER.info("Converted {} (made for pack format {}) in {} ms: {}", location.id(),
					meta.format(), result.nanos() / 1_000_000, result.context().counters());
				for (String warning : result.context().warnings()) PackConverterClient.LOGGER.warn("{}: {}", location.id(), warning);
			}
			return new ConvertedPackResources(location, source, entry);
		} catch (Exception e) {
			PackConverterClient.LOGGER.error("Could not convert {}, loading it unconverted", location.id(), e);
			if (source != null) {
				try {
					source.close();
				} catch (Exception ignored) {
				}
			}
			return null;
		}
	}

	/** The zip or folder a pack is read from, or null (built-in and mods' packs). */
	public static @Nullable Path pathOf(Pack pack) {
		return pathOf(((PackAccessor) pack).packconverter$resources());
	}

	private static @Nullable Path pathOf(Pack.ResourcesSupplier resources) {
		if (resources instanceof FileResourcesSupplierAccessor file) return file.packconverter$content().toPath();
		if (resources instanceof PathResourcesSupplierAccessor dir) return dir.packconverter$content();
		return null;
	}
}
