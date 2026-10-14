package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.packconverter.mc.folders.PackFolders;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.nio.file.Files;
import java.nio.file.Path;

/** With pack folders on, a folder in resourcepacks/ is expected: no "Found non-pack entry" line for it on every scan. */
@Mixin(FolderRepositorySource.class)
public abstract class FolderRepositorySourceMixin {
	@WrapOperation(method = "discoverPacks", require = 0,
		at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V"))
	private static void packconverter$quietFolders(Logger logger, String message, Object content, Operation<Void> original) {
		if (content instanceof Path path && PackFolders.enabled() && Files.isDirectory(path)) return;
		original.call(logger, message, content);
	}
}
