package dev.packconverter.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.packconverter.mc.FastReload;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/** Pack switches leave out the listeners whose resources didn't change ({@link FastReload}). */
@Mixin(ReloadableResourceManager.class)
public abstract class ReloadableResourceManagerMixin {
	@ModifyArg(method = "createReload", index = 1, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/server/packs/resources/SimpleReloadInstance;create(Lnet/minecraft/server/packs/resources/ResourceManager;Ljava/util/List;Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Z)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
	private List<PreparableReloadListener> packconverter$neededListeners(List<PreparableReloadListener> listeners,
		@Local(argsOnly = true) List<PackResources> packs) {
		// the game's own resources only (not an integrated server's data)
		if ((Object) this != Minecraft.getInstance().getResourceManager()) return listeners;
		return FastReload.listeners(listeners, packs);
	}
}
