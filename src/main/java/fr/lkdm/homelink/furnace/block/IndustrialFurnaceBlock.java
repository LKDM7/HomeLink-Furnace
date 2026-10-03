package fr.lkdm.homelink.furnace.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.menu.FurnaceMenu;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

public final class IndustrialFurnaceBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final IntegerProperty COLUMN=IntegerProperty.create("column",0,1), LAYER=IntegerProperty.create("layer",0,1), ROW=IntegerProperty.create("row",0,1);
    public static final MapCodec<IndustrialFurnaceBlock> CODEC=RecordCodecBuilder.mapCodec(i -> i.group(com.mojang.serialization.Codec.INT.fieldOf("tier").forGetter(b->b.tier.ordinal()),propertiesCodec()).apply(i,(t,p)->new IndustrialFurnaceBlock(FurnaceTier.values()[Math.clamp(t,0,2)],p)));
    static boolean rollback;
    private final FurnaceTier tier;
    public IndustrialFurnaceBlock(FurnaceTier tier,Properties properties) {super(properties);this.tier=tier;registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(COLUMN,0).setValue(LAYER,0).setValue(ROW,0));}
    public FurnaceTier tier(){return tier;}
    @Override protected MapCodec<? extends HorizontalDirectionalBlock> codec(){return CODEC;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> b){b.add(FACING,COLUMN,LAYER,ROW);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    public static boolean isMaster(BlockState s){return s.getValue(COLUMN)==0&&s.getValue(LAYER)==0&&s.getValue(ROW)==0;}
    public static BlockPos masterPos(BlockPos pos,BlockState s){return FurnaceLayout.master(pos,s.getValue(FACING),new FurnaceLayout.Cell(s.getValue(COLUMN),s.getValue(LAYER),s.getValue(ROW)));}
    public static IndustrialFurnaceBlockEntity master(Level level,BlockPos pos,BlockState state){if(!(state.getBlock() instanceof IndustrialFurnaceBlock))return null;var m=masterPos(pos,state);return level.hasChunkAt(m)&&level.getBlockEntity(m) instanceof IndustrialFurnaceBlockEntity e ? e:null;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return isMaster(state)?new IndustrialFurnaceBlockEntity(pos,state):null;}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l,BlockState s,BlockEntityType<T> t){if(l.isClientSide||!isMaster(s)||t!=FurnaceRegistries.MASTER.get())return null;return (world,pos,state,entity)->IndustrialFurnaceBlockEntity.serverTick(world,pos,state,(IndustrialFurnaceBlockEntity)entity);}
    @Override public void setPlacedBy(Level l,BlockPos p,BlockState s,LivingEntity placer,ItemStack stack){if(!l.isClientSide&&placer instanceof Player player){var e=master(l,p,s);if(e!=null)e.setOwner(player.getUUID(),player.getGameProfile().getName());}}
    @Override protected InteractionResult useWithoutItem(BlockState s,Level l,BlockPos p,Player player,BlockHitResult hit){
        if(l.isClientSide)return InteractionResult.SUCCESS;var e=master(l,p,s);
        if(player instanceof ServerPlayer sp&&e!=null&&e.mayView(player)){sp.openMenu(new SimpleMenuProvider((id,inv,pl)->new FurnaceMenu(id,inv,e),e.displayName()),buf->{buf.writeBlockPos(e.getBlockPos());buf.writeVarInt(e.tier().ordinal());});fr.lkdm.homelink.furnace.network.FurnacePayloads.sendNetworkChoices(sp,e);}
        return InteractionResult.CONSUME;
    }
    @Override public BlockState playerWillDestroy(Level l,BlockPos p,BlockState s,Player player){
        var e=master(l,p,s);
        if(!l.isClientSide){
            boolean drop=!player.isCreative()&&player.hasCorrectToolForDrops(s);
            if(e!=null){e.markPlayerBreak();if(drop)popResource(l,e.getBlockPos(),new ItemStack(e.getBlockState().getBlock()));}
            else if(l instanceof ServerLevel server&&!isMaster(s)&&mayHaveDeferredMaster(l,p,s))FurnaceDismantleRequests.get(server).recordPlayerIntent(p,drop,server.getGameTime());
        }
        return super.playerWillDestroy(l,p,s,player);
    }
    private static boolean mayHaveDeferredMaster(Level level,BlockPos part,BlockState state){
        var master=masterPos(part,state);
        if(!level.hasChunkAt(master))return true;
        var expected=level.getBlockState(master);
        return expected.is(state.getBlock())&&isMaster(expected)&&expected.getValue(FACING)==state.getValue(FACING);
    }
    @Override protected void onRemove(BlockState s,Level l,BlockPos p,BlockState replacement,boolean moved){
        if(!rollback&&!s.is(replacement.getBlock())&&!l.isClientSide){
            var e=master(l,p,s);
            if(e!=null)e.dismantle();
            else if(l instanceof ServerLevel server&&!isMaster(s)&&mayHaveDeferredMaster(l,p,s))FurnaceDismantleRequests.get(server).confirmRemoval(p,masterPos(p,s),s,server.getGameTime());
        }
        super.onRemove(s,l,p,replacement,moved);
    }
    @Override protected void onPlace(BlockState s,Level l,BlockPos p,BlockState old,boolean moved){super.onPlace(s,l,p,old,moved);if(!l.isClientSide&&!isMaster(s))l.scheduleTick(p,this,100);}
    @Override public void tick(BlockState s,ServerLevel l,BlockPos p,RandomSource random){var m=masterPos(p,s);if(l.hasChunkAt(m)&&!(l.getBlockEntity(m) instanceof IndustrialFurnaceBlockEntity))l.removeBlock(p,false);else l.scheduleTick(p,this,100);}
}
