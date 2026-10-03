package fr.lkdm.homelink.furnace.block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-local tombstones for explicit part removal while the master is unloaded. */
public final class FurnaceDismantleRequests extends SavedData {
    public static final String FILE_ID="homelink_furnace_dismantle";
    public record Request(ResourceLocation block,Direction facing,boolean dropMachine) {}
    private record PlayerIntent(boolean dropMachine,long tick) {}
    private final Map<BlockPos,Request> requests=new LinkedHashMap<>();
    /** A player callback is an intent, not a confirmed removal, and must never reach disk. */
    private final Map<BlockPos,PlayerIntent> playerIntents=new LinkedHashMap<>();
    private static final Factory<FurnaceDismantleRequests> FACTORY=new Factory<>(FurnaceDismantleRequests::new,(tag,registries)->load(tag));
    public FurnaceDismantleRequests() {}
    public static FurnaceDismantleRequests get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,FILE_ID);}
    public void recordPlayerIntent(BlockPos part,boolean dropMachine,long gameTick){
        if(playerIntents.size()>=1_024)playerIntents.remove(playerIntents.keySet().iterator().next());
        playerIntents.put(part.immutable(),new PlayerIntent(dropMachine,gameTick));
    }
    public void confirmRemoval(BlockPos part,BlockPos master,BlockState state,long gameTick){
        var player=playerIntents.remove(part);
        // Vanilla destruction confirms removal synchronously in the same server tick.
        // A cancelled earlier callback must not change a later non-player removal's loot.
        request(master,state,player==null||player.tick()!=gameTick||player.dropMachine());
    }
    public void request(BlockPos master,BlockState state,boolean dropMachine){
        if(!(state.getBlock() instanceof IndustrialFurnaceBlock))return;
        var request=new Request(BuiltInRegistries.BLOCK.getKey(state.getBlock()),state.getValue(IndustrialFurnaceBlock.FACING),dropMachine);
        if(requests.putIfAbsent(master.immutable(),request)==null)setDirty();
    }
    /** Consumed exactly once. A different block or orientation discards a stale request. */
    public Optional<Request> take(BlockPos master,BlockState state){
        var request=requests.remove(master);if(request==null)return Optional.empty();setDirty();
        if(!(state.getBlock() instanceof IndustrialFurnaceBlock)||!IndustrialFurnaceBlock.isMaster(state)
                ||!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(request.block())
                ||state.getValue(IndustrialFurnaceBlock.FACING)!=request.facing())return Optional.empty();
        return Optional.of(request);
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("version",1);var entries=new ListTag();
        for(var entry:requests.entrySet()){
            var t=new CompoundTag();t.putLong("master",entry.getKey().asLong());t.putString("block",entry.getValue().block().toString());
            t.putString("facing",entry.getValue().facing().getName());t.putBoolean("dropMachine",entry.getValue().dropMachine());entries.add(t);
        }
        tag.put("requests",entries);return tag;
    }
    public static FurnaceDismantleRequests load(CompoundTag tag){
        var data=new FurnaceDismantleRequests();if(tag.getInt("version")!=1)return data;
        var entries=tag.getList("requests",Tag.TAG_COMPOUND);
        for(int i=0;i<entries.size();i++){
            var t=entries.getCompound(i);var block=ResourceLocation.tryParse(t.getString("block"));var facing=Direction.byName(t.getString("facing"));
            if(!t.contains("master",Tag.TAG_LONG)||block==null||facing==null||facing.getAxis().isVertical())continue;
            data.requests.putIfAbsent(BlockPos.of(t.getLong("master")),new Request(block,facing,t.getBoolean("dropMachine")));
        }
        return data;
    }
}
