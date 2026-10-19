package dev.packconverter.mixin;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.packconverter.core.io.Json;
import net.minecraft.server.packs.AbstractPackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

/**
 * Old packs whose pack.mcmeta only Minecraft 1.8 and before could read (escapes JSON doesn't have, like
 * {@code "§b\! made by"}; line breaks inside the description) fail to load it, and the pack is left out of the list.
 * pack.mcmeta is read as usual first; only when that fails, it is read the lenient way 1.8 did ({@link Json#parse}),
 * and if that fails too the game gets its own error. Recursive Resources hooks another call in the same method (its
 * error log): both apply. Optional: without the target, nothing happens.
 */
@Mixin(AbstractPackResources.class)
public abstract class PackMetadataEscapesMixin {
	@WrapOperation(method = "getMetadataFromStream", require = 0, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/util/GsonHelper;parse(Ljava/io/Reader;)Lcom/google/gson/JsonObject;"))
	private static JsonObject packconverter$oldEscapes(Reader reader, Operation<JsonObject> original) throws IOException {
		StringWriter read = new StringWriter();
		reader.transferTo(read);
		String text = read.toString();
		try {
			return original.call(new StringReader(text));
		} catch (RuntimeException e) {
			JsonObject lenient = Json.obj(Json.parse(text));
			if (lenient == null) throw e;
			return lenient;
		}
	}
}
