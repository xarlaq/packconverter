package dev.packconverter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.packconverter.core.Options;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings, stored in config/packconverter.json. */
public final class PackConverterConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static PackConverterConfig instance;

	/** Convert packs made for older versions automatically when they are applied. */
	public boolean autoConvert = true;
	/** Folders in the resource pack screen: packs in subfolders of resourcepacks/ are listed, by folder. */
	public boolean packFolders = true;
	/** Hovering a pack in the resource pack screen shows its hotbar, hearts, crosshair and a few items. */
	public boolean packPreviews = true;
	/** Pack tooltips also list what was converted (old packs): for checking a conversion. */
	public boolean conversionDetails = false;
	/** Packs with diamond but no netherite items get netherite ones recoloured from diamond. */
	public boolean netheriteFromDiamond = true;
	/** The colour for those, as #rrggbb (vanilla netherite's by default). */
	public String netheriteColor = "#443940";
	/** Which material the pack previews show: "all" (one after another) or one of {@code PackPreview.MATERIAL_NAMES}. */
	public String previewMaterial = "all";
	/** Pack switches keep shaders and fonts when no changed pack has any, and the loading screen goes quicker. */
	public boolean fastReload = true;
	/** Disk space the conversion cache may use; the conversions used longest ago go first. */
	public int cacheLimitMb = 1024;
	/** Where "Convert and save" writes standalone converted packs (relative to the game folder, or absolute). */
	public String exportFolder = "resourcepacks/converted";

	public static synchronized PackConverterConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	/** The netherite colour as 0xRRGGBB (vanilla netherite's if the setting can't be read). */
	public int netheriteRgb() {
		try {
			return Integer.parseInt(netheriteColor.strip().replace("#", ""), 16) & 0xFFFFFF;
		} catch (RuntimeException e) {
			return Options.NETHERITE;
		}
	}

	/** The choices conversions in the game are made with. */
	public Options conversionOptions() {
		return new Options(netheriteFromDiamond, netheriteRgb());
	}

	public long cacheLimitBytes() {
		return Math.max(16, cacheLimitMb) * 1024L * 1024L;
	}

	public Path exportPath() {
		Path p = Path.of(exportFolder.isBlank() ? "resourcepacks/converted" : exportFolder);
		return p.isAbsolute() ? p : FabricLoader.getInstance().getGameDir().resolve(p).normalize();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("packconverter.json");
	}

	private static PackConverterConfig load() {
		Path file = file();
		if (Files.isRegularFile(file)) {
			try {
				PackConverterConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PackConverterConfig.class);
				if (config != null) return config;
			} catch (IOException | RuntimeException e) {
				PackConverterClient.LOGGER.warn("Could not read {}, using defaults", file, e);
			}
		}
		PackConverterConfig config = new PackConverterConfig();
		config.save();
		return config;
	}

	public void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			PackConverterClient.LOGGER.warn("Could not save {}", file(), e);
		}
	}
}
