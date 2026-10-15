package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.packconverter.mc.FastReload;
import dev.packconverter.mc.FrameSplit;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pack switches ({@link FastReload}): the pack list isn't read again, and their work can wait for the next frame. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** Work a pack switch put off to the next frame ({@link FrameSplit}). */
	@Inject(method = "runTick", at = @At("HEAD"))
	private void packconverter$frame(boolean advanceGameTime, CallbackInfo ci) {
		FrameSplit.frame();
	}

	/**
	 * The pack list was read when the pack screen opened (and again whenever the folder changed while it was open).
	 * Reading it means opening every pack in resourcepacks/, on the render thread, as the switch starts: the first of
	 * its two hitches, long with hundreds of packs.
	 */
	@WrapOperation(method = "reloadResourcePacks(ZLnet/minecraft/client/GameLoadCookie;)Ljava/util/concurrent/CompletableFuture;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/repository/PackRepository;reload()V"))
	private void packconverter$keepPackList(PackRepository repository, Operation<Void> original) {
		if (!FastReload.fastSwitch()) original.call(repository);
	}
}
