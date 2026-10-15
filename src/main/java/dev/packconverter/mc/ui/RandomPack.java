package dev.packconverter.mc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.packconverter.PackConverterConfig;
import dev.packconverter.mc.LegacyPacks;
import dev.packconverter.mc.folders.Favourites;
import dev.packconverter.mc.folders.PackFolders;
import dev.packconverter.mc.folders.ResourcePackScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * "Random pack": swaps the packs you use (the ones from the resourcepacks folder; built-in ones stay) for one picked
 * at random, and applies it. The die button on the resource pack screen picks from the list shown (the folder open,
 * or the search), the key (unbound at first; set in Controls, or by right-clicking the button) from every pack.
 */
public final class RandomPack {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("packconverter", "main"));
	public static final KeyMapping KEY = new KeyMapping("key.packconverter.random_pack", InputConstants.UNKNOWN.getValue(), CATEGORY);
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("packconverter", "icon/random");
	private static final SystemToast.SystemToastId NOTHING_TO_PICK = new SystemToast.SystemToastId();
	private static final Random RANDOM = new Random();
	/** A picked pack being converted before it's applied: further picks wait. */
	private static boolean preparing;
	/** The button waiting for a key after a right-click, if any. */
	private static @Nullable SpriteIconButton binding;

	private RandomPack() {
	}

	/** The setting: the button on the pack screen, and the key. */
	public static boolean enabled() {
		return PackConverterConfig.get().randomPack;
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KEY.consumeClick()) applyAny(client);
		});
	}

	/** The die button for a resource pack screen. */
	public static SpriteIconButton button(Screen screen) {
		SpriteIconButton button = SpriteIconButton.builder(Component.translatable("packconverter.random.button"), b -> applyShown(screen), true)
			.width(20)
			.sprite(ICON, 16, 16)
			.build();
		button.setOverrideRenderHighlightedSprite(() -> binding == button);
		binding = null;
		updateTooltip(button);
		return button;
	}

	/** Right-click on the button: wait for a key; while waiting, the next key or mouse button sets it. */
	public static void listen(Screen screen, ResourcePackScreen packs) {
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> mouseClicked(s, packs.packconverter$randomButton(), event));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> keyPressed(packs.packconverter$randomButton(), event));
	}

	private static boolean mouseClicked(Screen screen, @Nullable SpriteIconButton button, MouseButtonEvent event) {
		if (button == null) return true;
		if (binding == button) {
			// left or right click: never mind; other mouse buttons (wheel, side buttons) can be the key
			if (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT) bind(button, InputConstants.Type.MOUSE.getOrCreate(event.button()));
			else stopBinding(button);
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && button.visible && button.active && button.isMouseOver(event.x(), event.y())) {
			binding = button;
			screen.setFocused(null); // so the key doesn't also type into the search box
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
			updateTooltip(button);
			return false;
		}
		return true;
	}

	private static boolean keyPressed(@Nullable SpriteIconButton button, KeyEvent event) {
		if (button == null || binding != button) return true;
		bind(button, event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
		return false;
	}

	private static void bind(SpriteIconButton button, InputConstants.Key key) {
		Minecraft minecraft = Minecraft.getInstance();
		KEY.setKey(key);
		KeyMapping.resetMapping();
		minecraft.options.save();
		stopBinding(button);
	}

	private static void stopBinding(SpriteIconButton button) {
		binding = null;
		updateTooltip(button);
	}

	private static void updateTooltip(SpriteIconButton button) {
		Component text;
		if (binding == button) {
			text = Component.translatable("packconverter.random.binding");
		} else {
			Component key = KEY.isUnbound() ? Component.translatable("packconverter.random.no_key") : KEY.getTranslatedKeyMessage();
			text = Component.empty()
				.append(Component.translatable("packconverter.random.button"))
				.append("\n").append(Component.translatable(PackConverterConfig.get().randomFavouritesOnly ? "packconverter.random.info_favourites" : "packconverter.random.info")
					.withStyle(ChatFormatting.GRAY))
				.append("\n").append(Component.translatable("packconverter.random.key", key))
				.append("\n").append(Component.translatable("packconverter.random.bind_hint").withStyle(ChatFormatting.GRAY));
		}
		button.setTooltip(Tooltip.create(text));
	}

	/** From the pack screen: a pack from the list shown, through the screen's own model (so "Done" keeps it). */
	private static void applyShown(Screen screen) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.gui.overlay() != null || preparing || !(screen instanceof ResourcePackScreen packs)) return; // still on the last one
		PackSelectionModel model = packs.packconverter$model();
		String search = packs.packconverter$search().toLowerCase(Locale.ROOT);
		String folder = PackFolders.enabled() ? PackFolders.current() : "";
		List<PackSelectionModel.Entry> pool = model.getUnselected()
			.filter(e -> fromFolder(e.getId()) && allowed(e.getId()) && shown(e, folder, search))
			.toList();
		if (pool.isEmpty()) {
			nothingToPick(minecraft);
			return;
		}
		PackSelectionModel.Entry pick = pool.get(RANDOM.nextInt(pool.size()));
		for (PackSelectionModel.Entry e : model.getSelected().toList()) {
			if (fromFolder(e.getId()) && e.canUnselect()) e.unselect();
		}
		pick.select();
		// the model has set the repository's selection: apply it with the screen still open, once the pack is converted
		// (if "Done" comes first, that applies it)
		preparing = true;
		LegacyPacks.prepare(pick.getId()).whenComplete((v, t) -> minecraft.execute(() -> {
			preparing = false;
			minecraft.options.updateResourcePacks(minecraft.getResourcePackRepository());
		}));
	}

	/** In the list shown: matching the search (as the screen does), or else in the folder open (or below it). */
	private static boolean shown(PackSelectionModel.Entry entry, String folder, String search) {
		if (!search.isBlank()) {
			return entry.getId().toLowerCase(Locale.ROOT).contains(search)
				|| entry.getTitle().getString().toLowerCase(Locale.ROOT).contains(search)
				|| entry.getDescription().getString().toLowerCase(Locale.ROOT).contains(search);
		}
		String in = PackFolders.folderOf(entry.getId());
		return folder.isEmpty() || in.equals(folder) || in.startsWith(folder + "/");
	}

	/**
	 * With the key, in game: any pack (from the last time the packs were listed). Its name shows at once; it's applied
	 * once it's converted, in the background if it never was.
	 */
	private static void applyAny(Minecraft minecraft) {
		if (!enabled() || minecraft.gui.overlay() != null || preparing) return;
		PackRepository repository = minecraft.getResourcePackRepository();
		List<Pack> pool = repository.getAvailablePacks().stream()
			.filter(p -> fromFolder(p.getId()) && allowed(p.getId()) && !repository.getSelectedPacks().contains(p)).toList();
		if (pool.isEmpty()) {
			nothingToPick(minecraft);
			return;
		}
		Pack pick = pool.get(RANDOM.nextInt(pool.size()));
		if (minecraft.player != null) minecraft.player.sendOverlayMessage(Component.translatable("packconverter.random.applied", pick.getTitle()));
		preparing = true;
		LegacyPacks.prepare(pick.getId()).whenComplete((v, t) -> minecraft.execute(() -> {
			preparing = false;
			switchTo(minecraft, pick);
		}));
	}

	/** The packs from the folder swapped for this one, applied. */
	private static void switchTo(Minecraft minecraft, Pack pick) {
		PackRepository repository = minecraft.getResourcePackRepository();
		List<Pack> selected = new ArrayList<>(repository.getSelectedPacks());
		selected.removeIf(p -> fromFolder(p.getId()) && !p.isRequired());
		pick.getDefaultPosition().insert(selected, pick, Pack::selectionConfig, false);
		repository.setSelected(selected.stream().map(Pack::getId).toList());
		minecraft.options.updateResourcePacks(repository);
	}

	/** A pack the setting lets it pick: any, or only favourites. */
	private static boolean allowed(String packId) {
		return !PackConverterConfig.get().randomFavouritesOnly || Favourites.enabled() && Favourites.is(packId);
	}

	/** A pack from the resourcepacks folder (or a folder in it), not a built-in one. */
	private static boolean fromFolder(String packId) {
		return packId.startsWith("file/");
	}

	private static void nothingToPick(Minecraft minecraft) {
		SystemToast.addOrUpdate(minecraft.gui.toastManager(), NOTHING_TO_PICK, Component.translatable("packconverter.random.none_title"),
			Component.translatable("packconverter.random.none"));
	}
}
