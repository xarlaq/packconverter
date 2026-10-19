package dev.packconverter.mixin;

import dev.packconverter.mc.LegacyPacks;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


/**
 * Both ways a client resource pack enters the game (pack folder via readMetaAndCreate, server packs via
 * DownloadedPackSource) read metadata through readPackMetadata and are opened through open(); old packs open
 * converted only while the game loads them ({@link PackRepositoryMixin}).
 */
@Mixin(Pack.class)
public abstract class PackMixin {
	@Shadow
	@Final
	private PackLocationInfo location;

	@Shadow
	@Final
	private Pack.Metadata metadata;

	@Inject(method = "readPackMetadata", at = @At("RETURN"), cancellable = true)
	private static void packconverter$acceptOldPacks(PackLocationInfo location, Pack.ResourcesSupplier resources, PackFormat currentPackVersion,
		PackType type, CallbackInfoReturnable<Pack.Metadata> cir) {
		Pack.Metadata metadata = cir.getReturnValue();
		if (metadata == null || type != PackType.CLIENT_RESOURCES) return;
		Pack.Metadata converted = LegacyPacks.inspect(location, resources, metadata);
		if (converted != null) cir.setReturnValue(converted);
	}

	@Inject(method = "open", at = @At("HEAD"), cancellable = true)
	private void packconverter$openConverted(CallbackInfoReturnable<PackResources> cir) {
		PackResources converted = LegacyPacks.open(this.location, this.metadata);
		if (converted != null) cir.setReturnValue(converted);
	}
}
