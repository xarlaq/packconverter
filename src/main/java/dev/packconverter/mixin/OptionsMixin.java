package dev.packconverter.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.packconverter.mc.FastReload;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;

/** The pack screen applying a new selection: the reload it starts is a pack switch ({@link FastReload}). */
@Mixin(Options.class)
public abstract class OptionsMixin {
	@WrapMethod(method = "updateResourcePacks")
	private void packconverter$packSwitch(PackRepository repository, Operation<Void> original) {
		FastReload.whileSwitching(() -> original.call(repository));
	}
}
