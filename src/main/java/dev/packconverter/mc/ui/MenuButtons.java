package dev.packconverter.mc.ui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import dev.packconverter.mc.Lunar;
import dev.packconverter.mc.folders.ResourcePackScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Pack Converter button, in the rows of small icon buttons of the pause menu ({@code PauseScreenMixin}) and the
 * title screen (with friends, language, accessibility; the row is centred again), and on the resource pack screen:
 * between "Open Pack Folder" and "Done" ({@code PackSelectionScreenMixin}; on Lunar Client
 * placed here).
 */
public final class MenuButtons {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("packconverter", "icon/convert");
	private static final int SIZE = 20, GAP = 4;

	private MenuButtons() {
	}

	public static SpriteIconButton create(Screen parent) {
		return SpriteIconButton.builder(Component.translatable("packconverter.button.open"),
				b -> Minecraft.getInstance().gui.setScreen(new PackConverterScreen(parent)), true)
			.width(SIZE)
			.sprite(ICON, 16, 16)
			.withTootip()
			.build();
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen) addToTitleRow(screen);
			else if (Lunar.detected() && screen instanceof PackSelectionScreen && screen instanceof ResourcePackScreen packs
				&& packs.packconverter$isResourcePacks()) {
				addBesideOpenFolder(screen, packs);
			}
		});
	}

	/**
	 * Lunar Client lays the pack screen out its own way (search box at the bottom) and sets it up again on resize
	 * without removing the widgets: one button per screen, put back each time, placed from the current buttons every
	 * frame ({@link #placeOnLunar}).
	 */
	private static void addBesideOpenFolder(Screen screen, ResourcePackScreen packs) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		SpriteIconButton existing = packs.packconverter$button();
		SpriteIconButton button = existing != null ? existing : create(screen);
		packs.packconverter$setButton(button);
		widgets.remove(button);
		widgets.add(button);
		placeOnLunar(screen, button);
		ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> placeOnLunar(s, button));
	}

	/**
	 * In the gap right of "Open Pack Folder" (Lunar's resourcepacks.gg icon sits on its left, and takes clicks first),
	 * right of "Done" if that gap is too narrow, the top-left corner if neither is there.
	 */
	private static void placeOnLunar(Screen screen, SpriteIconButton button) {
		AbstractWidget open = find(screen, button, "pack.openFolder"), done = find(screen, button, "gui.done");
		if (open == null) {
			button.setPosition(6, 6);
			return;
		}
		int y = open.getY() + open.getHeight() / 2 - SIZE / 2, right = open.getX() + open.getWidth();
		if (done != null && done.getY() == open.getY() && done.getX() - right >= SIZE + 4) {
			button.setPosition(right + (done.getX() - right - SIZE) / 2, y);
		} else if (done != null) {
			button.setPosition(done.getX() + done.getWidth() + GAP, done.getY() + done.getHeight() / 2 - SIZE / 2);
		} else {
			button.setPosition(right + GAP, y);
		}
	}

	private static @Nullable AbstractWidget find(Screen screen, SpriteIconButton ours, String key) {
		String label = Component.translatable(key).getString();
		for (AbstractWidget w : Screens.getWidgets(screen)) {
			if (w == ours) continue;
			if (w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key) || w.getMessage().getString().equals(label)) return w;
		}
		return null;
	}

	private static void addToTitleRow(Screen screen) {
		List<AbstractWidget> widgets = Screens.getWidgets(screen);
		if (find(screen, null, "packconverter.button.open") != null) return; // shown again: already there
		AbstractWidget language = widgets.stream()
			.filter(w -> w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("options.language"))
			.findFirst().orElse(null);
		if (language == null) return;
		List<AbstractWidget> row = new ArrayList<>(widgets.stream()
			.filter(w -> w.getY() == language.getY() && w.getWidth() == SIZE && w.getHeight() == SIZE)
			.sorted(Comparator.comparingInt(AbstractWidget::getX)).toList());
		SpriteIconButton button = create(screen);
		row.add(button);
		widgets.add(button);
		int x = screen.width / 2 - (row.size() * (SIZE + GAP) - GAP) / 2;
		for (AbstractWidget w : row) {
			w.setPosition(x, language.getY());
			x += SIZE + GAP;
		}
	}
}
