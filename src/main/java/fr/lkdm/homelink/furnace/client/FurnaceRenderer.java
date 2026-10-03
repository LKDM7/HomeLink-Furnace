package fr.lkdm.homelink.furnace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lkdm.homelink.furnace.HomeLinkFurnace;
import fr.lkdm.homelink.furnace.block.IndustrialFurnaceBlock;
import fr.lkdm.homelink.furnace.block.FurnaceLayout;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.furnace.FurnaceHeat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.WeakHashMap;

/** Fan phase derives from client game time; no per-frame network or blockstate traffic. */
public final class FurnaceRenderer implements BlockEntityRenderer<IndustrialFurnaceBlockEntity> {
    private static final ModelResourceLocation FAN = ModelResourceLocation.standalone(HomeLinkFurnace.id("block/fan"));
    private static final ModelResourceLocation[] HEAT = {
            ModelResourceLocation.standalone(HomeLinkFurnace.id("block/heat_1")),
            ModelResourceLocation.standalone(HomeLinkFurnace.id("block/heat_2")),
            ModelResourceLocation.standalone(HomeLinkFurnace.id("block/heat_3"))
    };
    private final WeakHashMap<IndustrialFurnaceBlockEntity, Bounds> bounds = new WeakHashMap<>();
    private record Bounds(BlockState state, AABB box) { }
    public FurnaceRenderer(BlockEntityRendererProvider.Context context) { }
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(FAN);
        for (var model : HEAT) event.register(model);
    }
    @Override public AABB getRenderBoundingBox(IndustrialFurnaceBlockEntity furnace) {
        var state = furnace.getBlockState();
        var cached = bounds.get(furnace);
        if (cached != null && cached.state() == state) return cached.box();
        AABB box = new AABB(furnace.getBlockPos());
        var facing = state.getValue(IndustrialFurnaceBlock.FACING);
        for (var cell : FurnaceLayout.cells(furnace.tier()))
            box = box.minmax(new AABB(FurnaceLayout.position(furnace.getBlockPos(), facing, cell)));
        box = box.inflate(1.0 / 16.0);
        bounds.put(furnace, new Bounds(state, box));
        return box;
    }
    @Override public void render(IndustrialFurnaceBlockEntity furnace, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (furnace.getLevel() == null) return;
        var heat = furnace.visualHeat();
        boolean hot = heat != FurnaceHeat.State.COLD;
        double ticks = furnace.getLevel().getGameTime() + partialTick;
        float speed = heat == FurnaceHeat.State.PROCESSING ? 24 : hot ? 10 : 0;
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(-furnace.getBlockState().getValue(IndustrialFurnaceBlock.FACING).toYRot() + 180));
        // Both the cabinet mesh and heat mesh use the master's complete local coordinate system.
        pose.translate(-.5, 0, -.5);
        if (heat == FurnaceHeat.State.READY || heat == FurnaceHeat.State.PROCESSING) {
            draw(furnace, HEAT[furnace.tier().ordinal()], pose, buffers, 15728880);
        }
        float angle = (float)(ticks * speed % 360);
        switch (furnace.tier()) {
            case I -> fan(furnace, pose, buffers, 11.5, 3.1, .625f, angle, light);
            case II -> fan(furnace, pose, buffers, 27.5, 3.1, .625f, angle, light);
            case III -> {
                fan(furnace, pose, buffers, 19.5, 6.5, 1.1f, angle, light);
                fan(furnace, pose, buffers, 26.0, 6.5, 1.1f, angle, light);
            }
        }
        pose.popPose();
    }
    private void fan(IndustrialFurnaceBlockEntity furnace, PoseStack pose, MultiBufferSource buffers,
                     double x, double y, float scale, float angle, int light) {
        pose.pushPose();
        pose.translate(x / 16.0, y / 16.0, .35 / 16.0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        // The common fan has a two-pixel radius; cabinet-specific scales match its grille radius.
        pose.scale(scale, scale, scale);
        draw(furnace, FAN, pose, buffers, light);
        pose.popPose();
    }
    private void draw(IndustrialFurnaceBlockEntity furnace, ModelResourceLocation model, PoseStack pose,
                      MultiBufferSource buffers, int light) {
        var minecraft = Minecraft.getInstance();
        minecraft.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()),
                furnace.getBlockState(), minecraft.getModelManager().getModel(model), 1, 1, 1,
                light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, RenderType.cutout());
    }
}
