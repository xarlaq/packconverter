package dev.packconverter.core;

import dev.packconverter.core.steps.AtlasStep;
import dev.packconverter.core.steps.Blockstates113Step;
import dev.packconverter.core.steps.AlphaTestStep;
import dev.packconverter.core.steps.Display19Step;
import dev.packconverter.core.steps.CreativeInventory19Step;
import dev.packconverter.core.steps.Inventory19Step;
import dev.packconverter.core.steps.EntityLayoutStep;
import dev.packconverter.core.steps.FinishStep;
import dev.packconverter.core.steps.NetheriteStep;
import dev.packconverter.core.steps.GuiScalingStep;
import dev.packconverter.core.steps.ItemDefinitions1214Step;
import dev.packconverter.core.steps.LegacyDataSteps;
import dev.packconverter.core.steps.LegacyNamesStep;
import dev.packconverter.core.steps.PrefetchStep;
import dev.packconverter.core.steps.PrepareStep;
import dev.packconverter.core.steps.RenameStep;
import dev.packconverter.core.steps.SliceStep;
import dev.packconverter.core.steps.TextureSteps;
import dev.packconverter.core.steps.ValidateStep;
import dev.packconverter.core.steps.VanillaCopiesStep;

import java.util.List;

/** Every step in version order. Each one sees the pack as left by the previous ones. */
public final class Steps {
	private Steps() {
	}

	public static List<Step> all() {
		return List.of(
			new PrepareStep(),
			new LegacyNamesStep(),
			new VanillaCopiesStep(),
			new PrefetchStep(),
			// 1.9
			TextureSteps.compassClock19(),
			new Inventory19Step(),
			new CreativeInventory19Step(),
			TextureSteps.offhandHotbar19(),
			new Display19Step(),
			RenameStep.to("1.9.4"),
			RenameStep.to("1.10.2"),
			// 1.11 / 1.12
			RenameStep.to("1.11.2"),
			RenameStep.to("1.12.2"),
			// 1.13 (the Flattening)
			new Blockstates113Step(),
			RenameStep.to("1.13.2"),
			TextureSteps.squareFrames113(),
			TextureSteps.water113(),
			LegacyDataSteps.lang113(),
			LegacyDataSteps.optifine113(),
			// 1.14 - 1.19
			RenameStep.to("1.14.4"),
			SliceStep.v1_14(),
			TextureSteps.glint115(),
			TextureSteps.chest115(),
			RenameStep.to("1.15.2"),
			RenameStep.to("1.16.1"),
			RenameStep.to("1.16.5"),
			new AlphaTestStep(),
			RenameStep.to("1.17.1"),
			RenameStep.to("1.18.2"),
			RenameStep.to("1.19.2"),
			TextureSteps.creativeTabs1193(),
			RenameStep.to("1.19.3"),
			TextureSteps.slider1194(),
			RenameStep.to("1.19.4"),
			// 1.20
			TextureSteps.title120(),
			RenameStep.to("1.20.1"),
			SliceStep.v1_20_2(),
			RenameStep.to("1.20.2"),
			RenameStep.to("1.20.4"),
			SliceStep.v1_20_5(),
			TextureSteps.menuBackground1205(),
			RenameStep.to("1.20.6"),
			// 1.21
			RenameStep.to("1.21.1"),
			RenameStep.to("1.21.3"),
			RenameStep.to("1.21.4"),
			new ItemDefinitions1214Step(),
			TextureSteps.builtinEntity1214(),
			RenameStep.to("1.21.5"),
			RenameStep.to("1.21.6"),
			RenameStep.to("1.21.8"),
			RenameStep.to("1.21.10"),
			SliceStep.v1_21_11(),
			RenameStep.to("1.21.11"),
			TextureSteps.effectBackground11211(),
			// 26.x
			RenameStep.to("26.1.2"),
			SliceStep.v26_2(),
			TextureSteps.beds111(),
			RenameStep.to("26.2"),
			RenameStep.to("26.3")
		);
	}

	public static List<Step> finish() {
		return List.of(new EntityLayoutStep(), new NetheriteStep(), new AtlasStep(), new GuiScalingStep(), new ValidateStep(), new FinishStep());
	}
}
