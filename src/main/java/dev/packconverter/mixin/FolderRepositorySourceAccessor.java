package dev.packconverter.mixin;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.world.level.validation.DirectoryValidator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(FolderRepositorySource.class)
public interface FolderRepositorySourceAccessor {
	@Accessor("folder")
	Path packconverter$folder();

	@Accessor("packType")
	PackType packconverter$packType();

	@Accessor("validator")
	DirectoryValidator packconverter$validator();
}
