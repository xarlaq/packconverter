package dev.packconverter.core.steps;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.packconverter.core.Context;
import dev.packconverter.core.PackTree;
import dev.packconverter.core.Step;
import dev.packconverter.core.VanillaAssets;
import dev.packconverter.core.io.Json;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 1.9 reworked held items: the "thirdperson"/"firstperson" model transforms became per-hand
 * ("thirdperson_righthand", ...), with a different rotation order and new base values that Mojang tuned by
 * hand (so there's no exact formula between the two). A pack's held transform is therefore converted relative
 * to vanilla: unchanged ones take the new vanilla transform of the same item; customised ones (smaller or
 * lowered swords...) carry their difference from vanilla 1.8 over onto the new vanilla transform. Item models
 * built on "builtin/generated" also get the new vanilla parent, so ground/frame/head views keep sensible sizes.
 */
public final class Display19Step implements Step {
	private static final String[][] CONTEXTS = {{"thirdperson", "thirdperson_righthand"}, {"firstperson", "firstperson_righthand"}};

	/** Item kinds of 1.8 and their models: [1.8 vanilla example, 26.3 model]. */
	private static final String[][] KINDS = {
		{"item/apple", "item/generated"},
		{"item/diamond_sword", "item/handheld"},
		{"item/fishing_rod", "item/handheld_rod"},
		{"item/bow", "item/bow"},
		{"item/stone", "block/block"},
	};

	private static Map<String, JsonObject> reference18;

	@Override
	public int format() {
		return 4; // old keys only exist in 1.8 packs, but mislabeled packs are common: checked per file
	}

	@Override
	public String name() {
		return "1.9 held item transforms";
	}

	@Override
	public void apply(PackTree tree, Context ctx) {
		int converted = 0;
		for (String path : tree.paths(p -> p.startsWith("assets/") && p.contains("/models/") && p.endsWith(".json"))) {
			JsonObject model = Json.obj(tree.json(path));
			JsonObject display = Json.obj(model, "display");
			if (display == null || !(display.has("thirdperson") || display.has("firstperson"))) continue;
			String[] parts = path.split("/", 4);
			String ns = parts[1], id = parts[3].substring(0, parts[3].length() - ".json".length()); // parts: assets, ns, models, id.json

			JsonObject ref8 = ns.equals("minecraft") ? reference().get(id) : null;
			String[] kind = ref8 != null ? kindOf(ref8) : kindOf(display);
			if (ref8 == null) ref8 = reference().get(kind[0]);
			String forwarded = ns.equals("minecraft") ? RenameStep.forward("models/" + id + ".json") : null;
			String newId = forwarded != null ? forwarded.substring("models/".length(), forwarded.length() - ".json".length()) : null;
			JsonObject vanillaModel = newId != null ? ctx.vanilla().model(newId) : null;
			JsonObject display9 = effectiveDisplay(ctx.vanilla(), vanillaModel != null ? newId : kind[1]);

			for (String[] c : CONTEXTS) {
				JsonObject d8 = Json.obj(display.remove(c[0]));
				if (d8 == null || display.has(c[1])) continue;
				JsonObject r9 = Json.obj(display9.get(c[1]));
				if (r9 == null) continue; // nothing to anchor to: the parent's transform applies
				JsonObject r8 = Json.obj(ref8 != null ? ref8.get(c[0]) : null);
				display.add(c[1], r8 != null && same(d8, r8) ? r9.deepCopy() : transfer(d8, r8, r9));
			}
			String parent = Json.str(model, "parent");
			if (parent != null && strip(parent).equals("builtin/generated") && id.startsWith("item/")) {
				String newParent = vanillaModel != null ? Json.str(vanillaModel, "parent") : null;
				if (newParent == null || strip(newParent).equals("builtin/generated")) newParent = kind[1];
				model.addProperty("parent", newParent);
			}
			tree.putJson(path, model);
			converted++;
		}
		ctx.count("held item transforms converted", converted);
	}

	/** The 1.8 kind whose third-person (else first-person) transform is closest to this display. */
	private static String[] kindOf(JsonObject display) {
		String key = display.has("thirdperson") ? "thirdperson" : "firstperson";
		JsonObject mine = Json.obj(display.get(key));
		String[] best = KINDS[0];
		double bestDist = Double.MAX_VALUE;
		for (String[] kind : KINDS) {
			JsonObject ref = reference().get(kind[0]);
			JsonObject theirs = Json.obj(ref != null ? ref.get(key) : null);
			if (mine == null || theirs == null) continue;
			double d = dist(vec(mine, "rotation", 0), vec(theirs, "rotation", 0)) + 4 * dist(vec(mine, "translation", 0), vec(theirs, "translation", 0))
				+ 20 * dist(vec(mine, "scale", 1), vec(theirs, "scale", 1));
			if (d < bestDist) {
				bestDist = d;
				best = kind;
			}
		}
		return best;
	}

	/** A model's transforms with every context its parents define (the child's win). */
	private static JsonObject effectiveDisplay(VanillaAssets.Cached vanilla, String id) {
		JsonObject result = new JsonObject();
		for (int depth = 0; id != null && depth < 16; depth++) {
			JsonObject model = vanilla.model(id);
			if (model == null) break;
			JsonObject display = Json.obj(model, "display");
			if (display != null) {
				for (Map.Entry<String, JsonElement> e : display.entrySet()) if (!result.has(e.getKey())) result.add(e.getKey(), e.getValue());
			}
			id = Json.str(model, "parent");
		}
		return result;
	}

	/**
	 * The pack's change relative to vanilla 1.8, moved onto the new vanilla transform. Matrices follow each
	 * version's renderer: 1.8 translates, rotates Y-X-Z and scales, then draws the model at half size around its
	 * centre; 26.3 translates, rotates X-Y-Z and scales, then centres the model at full size.
	 */
	static JsonObject transfer(JsonObject d8, JsonObject r8, JsonObject r9) {
		Matrix4f m8d = matrix18(d8);
		Matrix4f m8r = r8 != null ? matrix18(r8) : new Matrix4f();
		Matrix4f delta = new Matrix4f(m8r).invert().mul(m8d);
		// 1.8 applied the delta to a half-size model: conjugate by that scale for the full-size one
		Matrix4f delta9 = new Matrix4f().scale(2).mul(delta).scale(0.5f);
		Matrix4f m = matrix9(r9).mul(delta9);
		return decompose(m);
	}

	static Matrix4f matrix18(JsonObject t) {
		float[] r = vec(t, "rotation", 0), tr = vec(t, "translation", 0), s = vec(t, "scale", 1);
		return new Matrix4f().translate(tr[0] / 16, tr[1] / 16, tr[2] / 16)
			.rotateY(rad(r[1])).rotateX(rad(r[0])).rotateZ(rad(r[2]))
			.scale(s[0], s[1], s[2]);
	}

	static Matrix4f matrix9(JsonObject t) {
		float[] r = vec(t, "rotation", 0), tr = vec(t, "translation", 0), s = vec(t, "scale", 1);
		return new Matrix4f().translate(tr[0] / 16, tr[1] / 16, tr[2] / 16)
			.rotateXYZ(rad(r[0]), rad(r[1]), rad(r[2]))
			.scale(s[0], s[1], s[2]);
	}

	/** For tests. */
	public static Matrix4f matrix9Public(JsonObject t) {
		return matrix9(t);
	}

	/** For tests. */
	public static JsonObject decomposePublic(Matrix4f m) {
		return decompose(m);
	}

	static JsonObject decompose(Matrix4f m) {
		Vector3f t = m.getTranslation(new Vector3f()).mul(16);
		Vector3f s = m.getScale(new Vector3f());
		if (m.determinant3x3() < 0) s.x = -s.x;
		Matrix3f rot = new Matrix3f();
		m.get3x3(rot);
		rot.scale(1 / s.x, 1 / s.y, 1 / s.z);
		Vector3f euler = eulerXYZ(rot);
		JsonObject out = new JsonObject();
		out.add("rotation", array((float) Math.toDegrees(euler.x), (float) Math.toDegrees(euler.y), (float) Math.toDegrees(euler.z)));
		out.add("translation", array(clamp(t.x, 80), clamp(t.y, 80), clamp(t.z, 80)));
		out.add("scale", array(clamp(s.x, 4), clamp(s.y, 4), clamp(s.z, 4)));
		return out;
	}

	/**
	 * Angles (radians) with R = Rx(x) * Ry(y) * Rz(z), the order 26.3 applies. Vanilla transforms often sit
	 * exactly at y = +-90 degrees, where only x +- z is defined: there x is kept at 0 and the angle goes to z,
	 * like vanilla's own values.
	 */
	static Vector3f eulerXYZ(Matrix3f r) {
		// JOML is column-major: mCR = column C, row R
		float sy = Math.max(-1, Math.min(1, r.m20));
		float y = (float) Math.asin(sy);
		float cy = (float) Math.cos(y);
		if (cy > 1e-4f) {
			return new Vector3f((float) Math.atan2(-r.m21, r.m22), y, (float) Math.atan2(-r.m10, r.m00));
		}
		return new Vector3f(0, y, (float) Math.atan2(r.m01, r.m11));
	}

	private static boolean same(JsonObject a, JsonObject b) {
		for (String key : new String[]{"rotation", "translation"}) {
			if (dist(vec(a, key, 0), vec(b, key, 0)) > 1e-3) return false;
		}
		return dist(vec(a, "scale", 1), vec(b, "scale", 1)) <= 1e-3;
	}

	static float[] vec(JsonObject o, String key, float def) {
		JsonArray a = Json.arr(o, key);
		float[] v = {def, def, def};
		if (a != null) {
			for (int i = 0; i < 3 && i < a.size(); i++) {
				Double d = Json.num(a.get(i));
				if (d != null) v[i] = d.floatValue();
			}
		}
		return v;
	}

	private static double dist(float[] a, float[] b) {
		return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) + Math.abs(a[2] - b[2]);
	}

	private static float rad(float deg) {
		return (float) Math.toRadians(deg);
	}

	private static float clamp(float v, float max) {
		return Math.max(-max, Math.min(max, v));
	}

	private static JsonArray array(float... values) {
		JsonArray a = new JsonArray();
		for (float v : values) a.add(Math.round(v * 1000) / 1000.0);
		return a;
	}

	private static String strip(String id) {
		return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
	}

	private static synchronized Map<String, JsonObject> reference() {
		if (reference18 == null) {
			Map<String, JsonObject> map = new HashMap<>();
			try (InputStream in = Display19Step.class.getResourceAsStream("/packconverter/display18.json")) {
				if (in == null) throw new IllegalStateException("display18.json missing from the mod jar");
				JsonObject models = Json.obj(Json.obj(Json.parse(in.readAllBytes())), "models");
				for (Map.Entry<String, JsonElement> e : models.entrySet()) map.put(e.getKey(), e.getValue().getAsJsonObject());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			reference18 = map;
		}
		return reference18;
	}
}
