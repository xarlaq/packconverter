package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.packconverter.core.io.Json;
import net.minecraft.server.packs.AbstractPackMetadataResources;
import net.minecraft.server.packs.resources.ResourceMetadata;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Old packs whose pack.mcmeta has escapes JSON doesn't have (a description like {@code "§b\! made by"}, read fine up
 * to 1.8) fail to load it, and the pack is left out of the list. pack.mcmeta is read as usual first; only when that
 * fails and the file has such escapes, it is read again with them fixed ({@link Json#fixEscapes}), else the game gets
 * its own error. Optional: without the target, nothing happens.
 */
@Mixin(AbstractPackMetadataResources.class)
public abstract class PackMetadataEscapesMixin {
	@WrapOperation(method = "loadMetadata", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/packs/resources/ResourceMetadata;fromJsonStream(Ljava/io/InputStream;)Lnet/minecraft/server/packs/resources/ResourceMetadata;"))
	private static ResourceMetadata packconverter$oldEscapes(InputStream stream, Operation<ResourceMetadata> original) throws IOException {
		byte[] data = stream.readAllBytes();
		try {
			return original.call(new ByteArrayInputStream(data));
		} catch (RuntimeException e) {
			String text = new String(data, StandardCharsets.UTF_8);
			String fixed = Json.fixEscapes(text);
			if (fixed == text) throw e;
			try {
				return original.call(new ByteArrayInputStream(fixed.getBytes(StandardCharsets.UTF_8)));
			} catch (RuntimeException again) {
				throw e;
			}
		}
	}
}
