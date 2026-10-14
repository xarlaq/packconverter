package dev.packconverter.mixin;

import com.google.common.collect.ImmutableSet;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.folders.NestedPackSource;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * openAllSelected is where the game opens packs to load them. Only there do old packs open converted; other
 * callers of Pack.open (pack manager mods finding a pack's folder, for one) get the pack as it is, with no
 * conversion on their thread.
 * <p>
 * The repository reading the resource pack folder also gets {@link NestedPackSource}, for packs in subfolders (not
 * with Pack Manager installed, which lists those itself).
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
	@Shadow
	@Final
	@Mutable
	private Set<RepositorySource> sources;

	@WrapMethod(method = "openAllSelected")
	private List<PackResources> packconverter$loading(Operation<List<PackResources>> original) {
		return LegacyPacks.whileLoading(original::call);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void packconverter$addFolders(RepositorySource[] given, CallbackInfo ci) {
		if (FabricLoader.getInstance().isModLoaded("packmanager")) return;
		for (RepositorySource source : this.sources) {
			if (source instanceof FolderRepositorySource && source instanceof FolderRepositorySourceAccessor folder
				&& folder.packconverter$packType() == PackType.CLIENT_RESOURCES) {
				Set<RepositorySource> withFolders = new LinkedHashSet<>(this.sources);
				withFolders.add(new NestedPackSource(folder.packconverter$folder(), folder.packconverter$validator()));
				this.sources = ImmutableSet.copyOf(withFolders);
				return;
			}
		}
	}
}
