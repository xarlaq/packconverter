package dev.packconverter.mixin;

import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** The sound files the sound manager opens sounds from (by file id), for {@code FastReload} to list them again. */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {
	@Accessor("soundCache")
	Map<Identifier, Resource> packconverter$soundCache();
}
