package dev.packconverter.mc.folders;

import dev.packconverter.PackConverterClient;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackDetector;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.level.validation.DirectoryValidator;
import net.minecraft.world.level.validation.ForbiddenSymlinkInfo;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Finds resource packs in subfolders of resourcepacks/ (vanilla only looks at the top). Runs when the game lists
 * packs (start, pack screen), never while playing. A folder is a pack if it has a pack.mcmeta, else a folder to look
 * into; symlinked folders aren't followed. Ids are "file/&lt;path&gt;", so same-named packs in two folders differ.
 */
public final class NestedPackSource implements RepositorySource {
	private static final PackSelectionConfig SELECTION = new PackSelectionConfig(false, Pack.Position.TOP, false);
	private static final int MAX_DEPTH = 8;
	private final Path root;
	private final DirectoryValidator validator;

	public NestedPackSource(Path root, DirectoryValidator validator) {
		this.root = root;
		this.validator = validator;
	}

	@Override
	public void loadPacks(Consumer<Pack> result) {
		if (!PackFolders.enabled() || !Files.isDirectory(this.root)) return;
		Detector detector = new Detector(this.validator);
		try (DirectoryStream<Path> top = Files.newDirectoryStream(this.root)) {
			for (Path entry : top) {
				if (isFolder(entry)) this.walk(entry, 1, detector, result);
			}
		} catch (IOException e) {
			PackConverterClient.LOGGER.warn("Could not list {}", this.root, e);
		}
	}

	private void walk(Path folder, int depth, Detector detector, Consumer<Pack> result) {
		if (depth > MAX_DEPTH) return;
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
			for (Path entry : entries) {
				if (isFolder(entry)) {
					this.walk(entry, depth + 1, detector, result);
					continue;
				}
				List<ForbiddenSymlinkInfo> issues = new ArrayList<>();
				Pack.ResourcesSupplier resources = detector.detectPackResources(entry, issues);
				if (resources == null || !issues.isEmpty()) continue;
				String id = "file/" + this.root.relativize(entry).toString().replace('\\', '/');
				String name = entry.getFileName().toString();
				Pack pack = Pack.readMetaAndCreate(new PackLocationInfo(id, Component.literal(name), PackSource.DEFAULT, Optional.empty()),
					resources, PackType.CLIENT_RESOURCES, SELECTION);
				if (pack != null) result.accept(pack);
			}
		} catch (IOException e) {
			PackConverterClient.LOGGER.warn("Could not list {}", folder, e);
		}
	}

	/** A plain folder (not a folder pack, not a link, not hidden). */
	private static boolean isFolder(Path path) {
		String name = path.getFileName().toString();
		return !name.startsWith(".") && !name.equals("__MACOSX") && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
			&& !Files.isRegularFile(path.resolve("pack.mcmeta"));
	}

	/** Vanilla's detection: zips and folders with a pack.mcmeta. */
	private static final class Detector extends PackDetector<Pack.ResourcesSupplier> {
		Detector(DirectoryValidator validator) {
			super(validator);
		}

		@Override
		protected Pack.ResourcesSupplier createZipPack(Path content) {
			return new FilePackResources.FileResourcesSupplier(content);
		}

		@Override
		protected Pack.ResourcesSupplier createDirectoryPack(Path content) {
			return new PathPackResources.PathResourcesSupplier(content);
		}
	}
}
