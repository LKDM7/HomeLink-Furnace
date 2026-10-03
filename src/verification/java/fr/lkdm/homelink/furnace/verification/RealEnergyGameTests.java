package fr.lkdm.homelink.furnace.verification;

import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homelink.furnace.block.IndustrialFurnaceBlock;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real HomeLink Energy transport with no imports of that mod's implementation. */
@GameTestHolder(FurnaceValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RealEnergyGameTests {
    @GameTest(template="empty",timeoutTicks=600)
    public static void batteryNetworkChargesOffThenRuns(GameTestHelper h){
        var level=h.getLevel();var p=h.absolutePos(new BlockPos(1,2,2));
        var cable=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("homelink_energy","copper_energy_cable"));
        var battery=BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("homelink_energy","battery_1"));
        h.assertTrue(cable!=Blocks.AIR&&battery!=Blocks.AIR,"Required HomeLink Energy not loaded");
        level.setBlockAndUpdate(p,FurnaceRegistries.FURNACE_I.get().defaultBlockState().setValue(IndustrialFurnaceBlock.FACING,Direction.NORTH));
        var furnace=(IndustrialFurnaceBlockEntity)level.getBlockEntity(p);furnace.setEnabled(false);
        level.setBlockAndUpdate(p.east(3),battery.defaultBlockState());
        var cell=level.getBlockEntity(p.east(3));var saved=cell.saveWithoutMetadata(level.registryAccess());saved.putLong("energy",2000);cell.loadWithComponents(saved,level.registryAccess());
        for(int i=1;i<=2;i++){level.setBlockAndUpdate(p.east(i).below(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(p.east(i),cable.defaultBlockState());}
        h.runAtTickTime(40,()->{
            h.assertTrue(furnace.energyStored()>0&&!furnace.enabled(),"Real HE network failed to charge switched-off furnace");
            h.assertTrue(furnace.processedTotal()==0,"OFF produced an item");
            furnace.input().setStackInSlot(0,new ItemStack(Items.RAW_IRON));furnace.setEnabled(true);
        });
        h.succeedWhen(()->{
            h.assertTrue(furnace.output().getStackInSlot(0).is(Items.IRON_INGOT),"Waiting for battery -> copper cable -> furnace production");
            var source=level.getCapability(EnergyApi.BLOCK,p.east(3),Direction.WEST);
            h.assertTrue(source!=null&&source.stored()+furnace.energyStored()<=1800,"HE network created energy");
            h.assertTrue(furnace.processedTotal()==1,"Unexpected real-network operation count");
        });
    }
}
