package dev.packconverter.mc.preview;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Matrix4fStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The 3D player of a pack preview: the player's own skin wearing one of the pack's armor sets, turning slowly. The
 * armor textures are regions of the preview's one texture: the armor models are drawn through a vertex consumer that
 * moves their texture coordinates into that region. Only runs while a preview tooltip is on screen; models are made on
 * first use.
 */
public final class ArmorPreviewRenderer extends PictureInPictureRenderer<ArmorPreviewRenderer.State> {
	private static final int FULL_BRIGHT = 15728880;
	/** Height (blocks above the feet) the model tilts about. */
	private static final float PIVOT = 1.0625F;

	/** Texture coordinates (0-1) of one armor texture within the preview's sheet. */
	public record Uv(float u0, float v0, float u1, float v1) {
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

	public ArmorPreviewRenderer(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
	}

	@Override
	public Class<State> getRenderStateClass() {
		return State.class;
	}

	@Override
	protected void renderToTexture(State state, PoseStack pose) {
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

		minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.PLAYER_SKIN);
		// tilt about the chest, as vanilla's skin preview does
		Matrix4fStack modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		modelView.rotateAround(Axis.XP.rotationDegrees(state.pitch()), 0.0F, state.scale() * minecraft.getWindow().getGuiScale() * PIVOT, 0.0F);
		pose.mulPose(Axis.YP.rotationDegrees(-state.rotation()));
		pose.translate(0.0F, -1.6010001F, 0.0F);
		Model.Simple body = skin.model() == PlayerModelType.SLIM ? this.slim : this.wide;
		body.renderToBuffer(pose, this.bufferSource.getBuffer(body.renderType(skin.body().texturePath())), FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
		VertexConsumer armorBuffer = this.bufferSource.getBuffer(RenderTypes.armorCutoutNoCull(state.sheet()));
		for (Model.Simple model : new Model.Simple[]{this.armor.head(), this.armor.chest(), this.armor.feet()}) {
			armor(model, state.armor(), state.tint(), armorBuffer, pose);
			armor(model, state.armorOverlay(), -1, armorBuffer, pose);
		}
		armor(this.armor.legs(), state.leggings(), state.tint(), armorBuffer, pose);
		armor(this.armor.legs(), state.leggingsOverlay(), -1, armorBuffer, pose);
		this.bufferSource.endBatch();
		modelView.popMatrix();
	}

	private static void armor(Model.Simple model, @Nullable Uv uv, int tint, VertexConsumer buffer, PoseStack pose) {
		if (uv != null) model.renderToBuffer(pose, new InRegion(buffer, uv), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, tint);
	}

	@Override
	protected String getTextureLabel() {
		return "pack preview player";
	}

	/** Passes vertices on with their texture coordinates moved into one region of the texture. */
	private record InRegion(VertexConsumer to, Uv uv) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			this.to.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int r, int g, int b, int a) {
			this.to.setColor(r, g, b, a);
			return this;
		}

		@Override
		public VertexConsumer setColor(int argb) {
			this.to.setColor(argb);
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			this.to.setUv(this.uv.u0() + (this.uv.u1() - this.uv.u0()) * u, this.uv.v0() + (this.uv.v1() - this.uv.v0()) * v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			this.to.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			this.to.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			this.to.setNormal(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setLineWidth(float width) {
			this.to.setLineWidth(width);
			return this;
		}
	}
}
