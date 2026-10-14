package dev.packconverter.mixin;

import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a pack's files are ({@code LegacyPacks.pathOf}): ids don't say (Lunar names packs in folders by file name). */
@Mixin(Pack.class)
public interface PackAccessor {
	@Accessor("resources")
	Pack.ResourcesSupplier packconverter$resources();
}
