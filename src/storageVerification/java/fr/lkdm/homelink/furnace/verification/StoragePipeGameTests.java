package fr.lkdm.homelink.furnace.verification;

import fr.lkdm.homelink.furnace.block.IndustrialFurnaceBlock;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import fr.lkdm.homelink.storage.logistics.filter.FilterMode;
import fr.lkdm.homelink.storage.logistics.filter.FlowMode;
import fr.lkdm.homelink.storage.logistics.network.PipeNetworkManager;
import fr.lkdm.homelink.storage.logistics.pipe.StoragePipeBlockEntity;
import fr.lkdm.homelink.storage.registry.StorageRegistries;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Optional actual Storage 1.4.0 pipe delivery, enabled only by -PwithStorage. */
@GameTestHolder(FurnaceValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StoragePipeGameTests {
    @GameTest(template="empty")
    public static void storagePipesFeedSmeltAndExtract(GameTestHelper h) {
        UUID owner=UUID.fromString("7ae5f0ef-cbe2-4a23-9a66-b57013a90563");
        var level=h.getLevel();var furnacePos=h.absolutePos(new BlockPos(3,2,3));
        var inputPipePos=furnacePos.above();var sourcePos=inputPipePos.west();
        var outputPipePos=furnacePos.south();var targetPos=outputPipePos.south();
        level.setBlockAndUpdate(furnacePos,FurnaceRegistries.FURNACE_I.get().defaultBlockState().setValue(IndustrialFurnaceBlock.FACING,Direction.NORTH));
        var furnace=(IndustrialFurnaceBlockEntity)level.getBlockEntity(furnacePos);
        furnace.setOwner(owner,"pipe_verifier");furnace.energyPort().insert(2000,false);furnace.serverTick();
        level.setBlockAndUpdate(sourcePos,Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(targetPos,Blocks.BARREL.defaultBlockState());
        level.setBlockAndUpdate(inputPipePos,StorageRegistries.PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(outputPipePos,StorageRegistries.PIPE.get().defaultBlockState());
        var input=(StoragePipeBlockEntity)level.getBlockEntity(inputPipePos);
        var output=(StoragePipeBlockEntity)level.getBlockEntity(outputPipePos);
        input.setOwner(owner);output.setOwner(owner);
        input.configOrCreate(Direction.WEST).apply(FlowMode.EXTRACT,FilterMode.BLACKLIST,Set.of(),owner,null,null,"minecraft:barrel");
        input.configOrCreate(Direction.DOWN).apply(FlowMode.INSERT,FilterMode.BLACKLIST,Set.of(),owner,null,null,"homelink_furnace:industrial_furnace_1");
        output.configOrCreate(Direction.NORTH).apply(FlowMode.EXTRACT,FilterMode.BLACKLIST,Set.of(),owner,null,null,"homelink_furnace:industrial_furnace_1");
        output.configOrCreate(Direction.SOUTH).apply(FlowMode.INSERT,FilterMode.BLACKLIST,Set.of(),owner,null,null,"minecraft:barrel");
        var source=(Container)level.getBlockEntity(sourcePos);var target=(Container)level.getBlockEntity(targetPos);
        source.setItem(0,new ItemStack(Items.RAW_IRON,2));
        var manager=PipeNetworkManager.get(level);manager.settleNow();
        for(int i=0;i<100;i++)manager.tick();
        h.assertTrue(source.isEmpty()&&furnace.queuedItems()==2,"Storage Pipe did not deliver smeltable cargo through INPUT");
        for(int i=0;i<450;i++)furnace.serverTick();
        // Synchronous fixture ticks keep world time fixed: explicitly schedule the next real dispatch round.
        manager.dispatchStandaloneSoon(owner);
        for(int i=0;i<100;i++)manager.tick();
        h.assertTrue(target.countItem(Items.IRON_INGOT)==2&&source.isEmpty()&&furnace.output().getStackInSlot(0).isEmpty(),"Storage Pipe did not extract real smelted output exactly once: target="+target.countItem(Items.IRON_INGOT)+", output="+furnace.output().getStackInSlot(0)+", cargo="+manager.ledger().packets().size());
        h.assertTrue(furnace.processedTotal()==2,"Pipe chain produced unexpected operation count");
        h.succeed();
    }
}
