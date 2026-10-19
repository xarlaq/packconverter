package dev.packconverter.mixin;

import com.google.gson.JsonObject;
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

/**
 * Old packs whose pack.mcmeta only Minecraft 1.8 and before could read (escapes JSON doesn't have, like
 * {@code "§b\! made by"}; line breaks inside the description) fail to load it, and the pack is left out of the list.
 * pack.mcmeta is read as usual first; only when that fails, it is read the lenient way 1.8 did ({@link Json#parse})
 * and handed to the game as plain JSON, and if that fails too the game gets its own error. Optional: without the
 * target, nothing happens.
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
			JsonObject lenient = Json.obj(Json.parse(data));
			if (lenient == null) throw e;
			return original.call(new ByteArrayInputStream(Json.toBytes(lenient)));
		}
	}
}
