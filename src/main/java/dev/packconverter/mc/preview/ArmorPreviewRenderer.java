package dev.packconverter.mc.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The 3D player of a pack preview: the player's own skin wearing one of the pack's armor sets, turning slowly. The
 * armor textures are regions of the preview's one texture. Only runs while a preview tooltip is on screen; models are
 * made on first use.
 */
public final class ArmorPreviewRenderer extends PictureInPictureRenderer<ArmorPreviewRenderer.State> {
	private static final int FULL_BRIGHT = 15728880;
	/** Height (blocks above the feet) the model tilts about. */
	private static final float PIVOT = 1.0625F;

	/** Texture coordinates (0-1) of one armor texture within the preview's sheet. */
	public record Uv(float u0, float v0, float u1, float v1) implements UvMapping {
		@Override
		public float getU(float offset) {
			return this.u0 + (this.u1 - this.u0) * offset;
		}

		@Override
		public float getV(float offset) {
			return this.v0 + (this.v1 - this.v0) * offset;
		}
	}

	/**
	 * One armor set: the texture of helmet, chestplate and boots, the leggings' one, the undyed overlays (leather), and the
	 * colour the first two are tinted with (leather's default brown, else white).
	 */
	public record State(Identifier sheet, Uv armor, Uv leggings, @Nullable Uv armorOverlay, @Nullable Uv leggingsOverlay, int tint,
		float rotation, float pitch, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
		implements PictureInPictureRenderState {
		public State(Identifier sheet, Uv armor, Uv leggings, @Nullable Uv armorOverlay, @Nullable Uv leggingsOverlay, int tint,
			float rotation, float pitch, int x0, int y0, int x1, int y1, float scale) {
			this(sheet, armor, leggings, armorOverlay, leggingsOverlay, tint, rotation, pitch, x0, y0, x1, y1, scale, null,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, null));
		}
	}

	private @Nullable EntityModelSet bakedFrom;
	private Model.@Nullable Simple wide;
	private Model.@Nullable Simple slim;
	private @Nullable ArmorModelSet<Model.Simple> armor;
	private @Nullable Supplier<PlayerSkin> skin;

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
		Minecraft minecraft = Minecraft.getInstance();
		EntityModelSet models = minecraft.getEntityModels();
		if (models != this.bakedFrom) {
			// made the first time, and again after a resource reload replaced the model set
			this.bakedFrom = models;
			this.wide = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
			this.slim = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
			this.armor = ModelLayers.PLAYER_ARMOR.map(layer -> new Model.Simple(models.bakeLayer(layer), RenderTypes::armorCutoutNoCull));
		}
		if (this.skin == null) this.skin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false);
		PlayerSkin skin = this.skin.get();

		minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.PLAYER_SKIN);
		// tilt about the chest, as vanilla's skin preview does
		int guiScale = minecraft.gameRenderer.gameRenderState().windowRenderState.guiScale;
		RenderSystem.getModelViewStack().rotateAround(Axis.XP.rotationDegrees(state.pitch()), 0.0F, state.scale() * guiScale * PIVOT, 0.0F);
		pose.rotateDegrees(Axis.YP, -state.rotation());
		pose.translate(0.0F, -1.6010001F, 0.0F);
		Model.Simple body = skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
		collector.submitModel(body, Unit.INSTANCE, pose, skin.body().texturePath(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
		RenderType armorType = RenderTypes.armorCutoutNoCull(state.sheet());
		for (Model.Simple model : new Model.Simple[]{this.armor.head(), this.armor.chest(), this.armor.feet()}) {
			this.armor(collector, model, state.armor(), state.tint(), armorType, pose);
			this.armor(collector, model, state.armorOverlay(), -1, armorType, pose);
		}
		this.armor(collector, this.armor.legs(), state.leggings(), state.tint(), armorType, pose);
		this.armor(collector, this.armor.legs(), state.leggingsOverlay(), -1, armorType, pose);
	}

	private void armor(SubmitNodeCollector collector, Model.Simple model, @Nullable Uv uv, int tint, RenderType type, PoseStack pose) {
		if (uv != null) collector.submitModel(model, Unit.INSTANCE, pose, type, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint, uv, 0);
	}

	@Override
	protected String getTextureLabel() {
		return "pack preview player";
	}
}
