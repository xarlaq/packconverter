package dev.packconverter.core.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.io.Json;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A knockback sword (a setting, on by default): diamond swords with Knockback II or more look different, the pack's
 * own diamond sword recoloured (red by default), the way {@link NetheriteStep} recolours diamond. The game picks the
 * look itself: the sword's item definition gets a condition on its enchantments, so a saved pack does it without
 * this mod too. Only for packs that have a diamond sword.
 */
public final class KnockbackSwordStep implements Step {
	private static final String TEXTURE = "assets/minecraft/textures/item/diamond_sword.png";
	private static final String MODEL = "assets/minecraft/models/item/diamond_sword.json";
	private static final String DEFINITION = "assets/minecraft/items/diamond_sword.json";
	/** Ours, in our own namespace (the items atlas reads item/ of every namespace). */
	public static final String KNOCKBACK_TEXTURE = "assets/packconverter/textures/item/knockback_diamond_sword.png";
	public static final String KNOCKBACK_MODEL = "assets/packconverter/models/item/knockback_diamond_sword.json";
	/** Knockback from this level up. */
	public static final int LEVEL = 2;

	@Override
	public String name() {
		return "knockback sword";
	}

	@Override
	public int format() {
		return Integer.MAX_VALUE;
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		if (!ctx.options().knockback() || !tree.has(TEXTURE) || tree.has(KNOCKBACK_TEXTURE)) return;
		BufferedImage sword = tree.image(TEXTURE);
		if (sword == null) return;
		JsonObject definition = Json.obj(tree.has(DEFINITION) ? tree.json(DEFINITION) : ctx.vanilla().json(DEFINITION));
		JsonElement plain = definition == null ? null : definition.get("model");
		if (plain == null) return;
		// the hue the pack's diamond art shares, as for netherite (a gem in the hilt keeps its colour)
		List<BufferedImage> diamond = new ArrayList<>();
		for (String item : NetheriteStep.ITEMS) {
			String path = "assets/minecraft/textures/item/diamond_" + item + ".png";
			BufferedImage img = tree.has(path) ? tree.image(path) : null;
			if (img != null) diamond.add(img);
		}
		tree.putImage(KNOCKBACK_TEXTURE, NetheriteStep.recolor(sword, ctx.options().knockbackColor(), NetheriteStep.mainHue(diamond)));
		if (tree.has(TEXTURE + ".mcmeta")) tree.copy(TEXTURE + ".mcmeta", KNOCKBACK_TEXTURE + ".mcmeta");
		tree.putJson(KNOCKBACK_MODEL, model(tree));

		JsonObject out = definition.deepCopy();
		out.add("model", condition(plain));
		tree.putJson(DEFINITION, out);
		ctx.count("knockback sword made from diamond", 1);
	}

	/**
	 * The diamond sword's model (the pack's, or vanilla's when it has none) with our texture: every texture of it that
	 * was the diamond sword's. As its child, so it holds and looks the same.
	 */
	private static JsonObject model(PackTree tree) {
		JsonObject textures = new JsonObject();
		JsonObject own = tree.has(MODEL) ? Json.obj(Json.obj(tree.json(MODEL)), "textures") : null;
		if (own != null) {
			for (Map.Entry<String, JsonElement> e : own.entrySet()) {
				String texture = Json.str(e.getValue());
				if (texture != null && (texture.startsWith("minecraft:") ? texture.substring(10) : texture).equals("item/diamond_sword")) {
					textures.addProperty(e.getKey(), "packconverter:item/knockback_diamond_sword");
				}
			}
		}
		if (textures.isEmpty()) textures.addProperty("layer0", "packconverter:item/knockback_diamond_sword");
		JsonObject model = new JsonObject();
		model.addProperty("parent", "minecraft:item/diamond_sword");
		model.add("textures", textures);
		return model;
	}

	/** Our model when the sword has Knockback {@link #LEVEL} or more, else the one it had. */
	private static JsonObject condition(JsonElement plain) {
		JsonObject knockback = new JsonObject();
		knockback.addProperty("enchantments", "minecraft:knockback");
		JsonObject levels = new JsonObject();
		levels.addProperty("min", LEVEL);
		knockback.add("levels", levels);
		JsonArray value = new JsonArray();
		value.add(knockback);
		JsonObject onTrue = new JsonObject();
		onTrue.addProperty("type", "minecraft:model");
		onTrue.addProperty("model", "packconverter:item/knockback_diamond_sword");
		JsonObject condition = new JsonObject();
		condition.addProperty("type", "minecraft:condition");
		condition.addProperty("property", "minecraft:component");
		condition.addProperty("predicate", "minecraft:enchantments");
		condition.add("value", value);
		condition.add("on_true", onTrue);
		condition.add("on_false", plain.deepCopy());
		return condition;
	}
}
