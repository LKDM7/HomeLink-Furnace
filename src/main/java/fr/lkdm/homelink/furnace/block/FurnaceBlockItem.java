package fr.lkdm.homelink.furnace.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Validate all loaded cells before changing the world; roll back if any placement fails. */
public final class FurnaceBlockItem extends BlockItem {
    public FurnaceBlockItem(IndustrialFurnaceBlock block,Properties properties) { super(block,properties); }
    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        var level=context.getLevel(); var block=(IndustrialFurnaceBlock)getBlock(); var facing=state.getValue(IndustrialFurnaceBlock.FACING);
        List<BlockPos> positions=new ArrayList<>();List<BlockState> old=new ArrayList<>();
        for (var cell:FurnaceLayout.cells(block.tier())) {
            var pos=FurnaceLayout.position(context.getClickedPos(),facing,cell);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) return false;
            var previous=level.getBlockState(pos);
            var cellContext=BlockPlaceContext.at(context,pos,context.getClickedFace());
            var part=state.setValue(IndustrialFurnaceBlock.COLUMN,cell.column()).setValue(IndustrialFurnaceBlock.LAYER,cell.layer()).setValue(IndustrialFurnaceBlock.ROW,cell.row());
            var collision=context.getPlayer()==null?net.minecraft.world.phys.shapes.CollisionContext.empty():net.minecraft.world.phys.shapes.CollisionContext.of(context.getPlayer());
            if (!previous.canBeReplaced(cellContext) || !level.isUnobstructed(part,pos,collision)) return false;
            positions.add(pos);old.add(previous);
        }
        int index=0;
        for (var cell:FurnaceLayout.cells(block.tier())) {
            var part=state.setValue(IndustrialFurnaceBlock.COLUMN,cell.column()).setValue(IndustrialFurnaceBlock.LAYER,cell.layer()).setValue(IndustrialFurnaceBlock.ROW,cell.row());
            if (!level.setBlock(positions.get(index),part,3)) {
                IndustrialFurnaceBlock.rollback=true;
                try { for(int i=0;i<index;i++)level.setBlock(positions.get(i),old.get(i),3); } finally { IndustrialFurnaceBlock.rollback=false; }
                return false;
            }
            index++;
        }
        return true;
    }
}
