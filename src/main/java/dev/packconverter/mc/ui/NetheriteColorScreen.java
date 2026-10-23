package dev.packconverter.mc.ui;

import dev.packconverter.PackConverterClient;
import dev.packconverter.core.Options;
import dev.packconverter.mc.preview.ArmorPreviewRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.IntConsumer;

/**
 * Picks the colour netherite is made in from diamond, with a live preview: the pack's diamond items, the same as
 * netherite, and a player in the netherite armor. Hue, saturation and lightness sliders, a hex box and a few presets.
 * Opened from the settings (vanilla's items) and before saving a converted pack (that pack's items). The knockback
 * sword's colour is picked the same way, with the diamond sword and its recoloured self for the preview.
 */
public final class NetheriteColorScreen extends Screen {
	/** What the colour is for: its title, the colour Reset goes back to, and the presets. */
	public enum Kind {
		/** Netherite first, then a few darker colours that suit a "better than diamond" tier. */
		NETHERITE("packconverter.netherite.title", NetheriteSample.Material.NETHERITE, Options.NETHERITE, 0x1E1B1F, 0x4A1F1F, 0x3A2850, 0x1F2C4A),
		/** Red first, then other colours that stand out from diamond. */
		KNOCKBACK("packconverter.knockback.title", NetheriteSample.Material.NETHERITE, Options.KNOCKBACK, 0xE0661A, 0xE0C21A, 0x8E2BD6, 0x1F9E4A),
		/** Copper first, then its exposed, weathered and oxidized colours. */
		COPPER("packconverter.copper.title", NetheriteSample.Material.COPPER, Options.COPPER, 0xA17E68, 0x6D9A78, 0x4FA58B);

		private final String title;
		private final NetheriteSample.Material material;
		private final int reset;
		private final int[] presets;

		Kind(String title, NetheriteSample.Material material, int reset, int... others) {
			this.title = title;
			this.material = material;
			this.reset = reset;
			this.presets = new int[others.length + 1];
			this.presets[0] = reset;
			System.arraycopy(others, 0, this.presets, 1, others.length);
		}
	}

	private final Kind kind;

	private final Screen parent;
	private final @Nullable Path pack;
	private final Component doneLabel;
	private final IntConsumer done;
	private int color;
	/** Hue (0-360), saturation and lightness (0-1) of {@link #color}, kept apart so a grey keeps its hue on the slider. */
	private float hue, saturation, lightness;
	private @Nullable CompletableFuture<NetheriteSample.Images> loading;
	private @Nullable NetheriteSample sample;
	private @Nullable Slider hueSlider, saturationSlider, lightnessSlider;
	private @Nullable EditBox hex;
	private boolean updating;
	/** The model turns by itself until it is dragged; then it stays where it was left. */
	private float yaw = 25, pitch = -5;
	private boolean turned, turning;

	/**
	 * @param pack whose diamond items to show (null: vanilla's)
	 * @param doneLabel the confirm button ("Done", or "Convert and save")
	 * @param done gets the chosen colour (0xRRGGBB) when confirmed
	 */
	public NetheriteColorScreen(Screen parent, @Nullable Path pack, int color, Component doneLabel, IntConsumer done) {
		this(parent, Kind.NETHERITE, pack, color, doneLabel, done);
	}

	public NetheriteColorScreen(Screen parent, Kind kind, @Nullable Path pack, int color, Component doneLabel, IntConsumer done) {
		super(Component.translatable(kind.title));
		this.kind = kind;
		this.parent = parent;
		this.pack = pack;
		this.doneLabel = doneLabel;
		this.done = done;
		this.setColor(color);
	}

	@Override
	protected void init() {
		if (this.loading == null) {
			this.loading = CompletableFuture.supplyAsync(() -> {
				try {
					return NetheriteSample.read(this.pack, this.kind.material);
				} catch (Exception e) {
					PackConverterClient.LOGGER.warn("Could not read the diamond items of {}", this.pack, e);
					return null;
				}
			}, Util.backgroundExecutor());
		}
		int cx = this.width / 2;
		this.hex = this.addRenderableWidget(new EditBox(this.font, cx - 30, 116, 64, 20, Component.translatable("packconverter.netherite.hex")));
		this.hex.setMaxLength(7);
		this.hex.setValue(hex(this.color));
		this.hex.setResponder(text -> {
			if (this.updating) return;
			String digits = text.strip().replace("#", "");
			if (digits.length() != 6) return;
			try {
				this.setColor(Integer.parseInt(digits, 16));
				this.syncWidgets(false);
			} catch (NumberFormatException ignored) {
			}
		});
		for (int i = 0; i < this.kind.presets.length; i++) {
			int preset = this.kind.presets[i];
			ColorSwatch swatch = this.addRenderableWidget(new ColorSwatch(15, 15, () -> preset, Component.literal(hex(preset)), () -> {
				this.setColor(preset);
				this.syncWidgets(true);
			}));
			swatch.setPosition(cx + 42 + i * 17, 118);
		}
		this.hueSlider = this.addRenderableWidget(new Slider(cx - 125, 140, this.hue / 360,
			v -> Component.translatable("packconverter.netherite.hue", Math.round(v * 360)), v -> {
			this.hue = (float) v * 360;
			this.colorFromSliders();
		}));
		this.saturationSlider = this.addRenderableWidget(new Slider(cx - 125, 162, this.saturation,
			v -> Component.translatable("packconverter.netherite.saturation", Math.round(v * 100)), v -> {
			this.saturation = (float) v;
			this.colorFromSliders();
		}));
		this.lightnessSlider = this.addRenderableWidget(new Slider(cx - 125, 184, this.lightness,
			v -> Component.translatable("packconverter.netherite.lightness", Math.round(v * 100)), v -> {
			this.lightness = (float) v;
			this.colorFromSliders();
		}));
		int y = this.height - 26;
		this.addRenderableWidget(Button.builder(Component.translatable("packconverter.netherite.reset"), b -> {
			this.setColor(this.kind.reset);
			this.syncWidgets(true);
		}).bounds(cx - 125, y, 70, 20).build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).bounds(cx - 50, y, 70, 20).build());
		this.addRenderableWidget(Button.builder(this.doneLabel, b -> {
			this.minecraft.gui.setScreen(this.parent);
			this.done.accept(this.color);
		}).bounds(cx + 25, y, 100, 20).build());
	}

	private void setColor(int rgb) {
		this.color = rgb & 0xFFFFFF;
		float[] hsl = hsl(this.color);
		this.hue = hsl[0];
		this.saturation = hsl[1];
		this.lightness = hsl[2];
		if (this.sample != null) this.sample.recolor(this.color);
	}

	private void colorFromSliders() {
		if (this.updating) return;
		this.color = rgb(this.hue, this.saturation, this.lightness);
		if (this.sample != null) this.sample.recolor(this.color);
		this.updating = true;
		if (this.hex != null) this.hex.setValue(hex(this.color));
		this.updating = false;
	}

	/** Puts the colour into the sliders and (if {@code text}) the hex box, without them answering back. */
	private void syncWidgets(boolean text) {
		this.updating = true;
		if (this.hueSlider != null) this.hueSlider.set(this.hue / 360);
		if (this.saturationSlider != null) this.saturationSlider.set(this.saturation);
		if (this.lightnessSlider != null) this.lightnessSlider.set(this.lightness);
		if (text && this.hex != null) this.hex.setValue(hex(this.color));
		this.updating = false;
	}

	/** True once the diamond items are read and shown (for tests). */
	public boolean ready() {
		return this.sample != null;
	}

	@Override
	public void tick() {
		if (this.sample == null && this.loading != null && this.loading.isDone()) {
			NetheriteSample.Images images = this.loading.getNow(null);
			if (images != null) {
				this.sample = new NetheriteSample(images, this.kind.material);
				this.sample.recolor(this.color);
			}
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		int cx = this.width / 2;
		graphics.centeredText(this.font, this.title, cx, 8, 0xFFFFFFFF);
		// the colour itself, next to its hex
		graphics.fill(cx - 55, 116, cx - 35, 136, 0xFF000000 | this.color);
		graphics.outline(cx - 55, 116, 20, 20, 0xFFA0A0A0);
		if (this.sample == null) {
			graphics.centeredText(this.font, Component.translatable(this.kind == Kind.COPPER ? "packconverter.copper.loading" : "packconverter.netherite.loading").withStyle(ChatFormatting.GRAY), cx + 35, 60, 0xFFFFFFFF);
			return;
		}
		if (this.kind == Kind.KNOCKBACK) {
			// the diamond sword, and the knockback one big beside it
			int sword = NetheriteSample.ITEMS.indexOf("sword");
			this.sample.drawItem(graphics, sword, false, cx - 85, 51, 32);
			this.sample.drawItem(graphics, sword, true, cx - 25, 35, 64);
			return;
		}
		long now = Util.getMillis();
		if (!this.turned) this.yaw = 25 + (now % 10_000) * 0.036F;
		graphics.guiRenderState.addPicturesInPictureState(new ArmorPreviewRenderer.State(this.sample.texture(), this.sample.armor(0),
			this.sample.armor(1), null, null, -1, this.yaw, this.pitch, cx - 125, 22, cx - 65, 112, 41));
		// the diamond items small, the netherite ones twice their size below
		for (int i = 0; i < NetheriteSample.ITEMS.size(); i++) {
			this.sample.drawItem(graphics, i, false, cx - 55 + i * 20, 24, 16);
			this.sample.drawItem(graphics, i, true, cx - 55 + (i % 5) * 36, 44 + (i / 5) * 34, 32);
		}
	}

	/** Dragging over the model turns it (sideways) and tilts it (up and down), like vanilla's skin preview. */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int cx = this.width / 2;
		if (this.kind == Kind.NETHERITE && this.sample != null && event.x() >= cx - 125 && event.x() < cx - 65 && event.y() >= 22 && event.y() < 112) {
			this.turning = true;
			this.turned = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!this.turning) return super.mouseDragged(event, dx, dy);
		this.yaw += (float) dx * 2.5F;
		this.pitch = Math.clamp(this.pitch - (float) dy * 2.5F, -50, 50);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		this.turning = false;
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public void removed() {
		if (this.sample != null) this.sample.close();
		this.sample = null;
		if (this.loading != null && !this.loading.isDone()) this.loading.cancel(false);
	}

	private static String hex(int rgb) {
		return String.format("#%06x", rgb & 0xFFFFFF);
	}

	/** Hue (degrees), saturation and lightness of 0xRRGGBB. */
	static float[] hsl(int rgb) {
		float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float l = (max + min) / 2, d = max - min, h = 0, s = 0;
		if (d > 0) {
			s = l > 0.5F ? d / (2 - max - min) : d / (max + min);
			if (max == r) h = ((g - b) / d) % 6;
			else if (max == g) h = (b - r) / d + 2;
			else h = (r - g) / d + 4;
			h *= 60;
			if (h < 0) h += 360;
		}
		return new float[]{h, s, l};
	}

	static int rgb(float h, float s, float l) {
		float c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs((h / 60) % 2 - 1)), m = l - c / 2;
		float[] rgb = h < 60 ? new float[]{c, x, 0} : h < 120 ? new float[]{x, c, 0} : h < 180 ? new float[]{0, c, x}
			: h < 240 ? new float[]{0, x, c} : h < 300 ? new float[]{x, 0, c} : new float[]{c, 0, x};
		int out = 0;
		for (float v : rgb) out = out << 8 | Math.round(Math.max(0, Math.min(1, v + m)) * 255);
		return out;
	}

	private static final class Slider extends AbstractSliderButton {
		private final DoubleFunction<Component> label;
		private final DoubleConsumer apply;

		Slider(int x, int y, double value, DoubleFunction<Component> label, DoubleConsumer apply) {
			super(x, y, 250, 20, label.apply(value), value);
			this.label = label;
			this.apply = apply;
		}

		void set(double value) {
			this.value = Math.max(0, Math.min(1, value));
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			this.setMessage(this.label.apply(this.value));
		}

		@Override
		protected void applyValue() {
			this.apply.accept(this.value);
		}
	}
}
