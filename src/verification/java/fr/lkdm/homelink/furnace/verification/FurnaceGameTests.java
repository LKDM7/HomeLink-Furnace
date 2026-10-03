package fr.lkdm.homelink.furnace.verification;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.energy.*;
import fr.lkdm.homecore.api.item.*;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homecore.api.production.ProductionReceipt;
import fr.lkdm.homelink.furnace.block.*;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.furnace.*;
import fr.lkdm.homelink.furnace.homelink.FurnaceDevice;
import fr.lkdm.homelink.furnace.menu.FurnaceMenu;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.*;
import net.neoforged.neoforge.gametest.*;

/** Real server recipes, capabilities, placement, persistence and public HomeCore contracts. */
@GameTestHolder(FurnaceValidation.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FurnaceGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final UUID OWNER = UUID.fromString("c6720342-a636-45f4-aa24-d45872103a53");
    private static final Direction FACING = Direction.NORTH;

    private static IndustrialFurnaceBlock block(FurnaceTier tier) { return FurnaceRegistries.FURNACES.get(tier.ordinal()).get(); }
    private static FakePlayer player(GameTestHelper h) {
        FakePlayer player = FakePlayerFactory.get(h.getLevel(), new GameProfile(OWNER, "furnace_verifier"));
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(h.absolutePos(POS).getCenter().add(0, 0, -3));
        player.setYRot(0); player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        return player;
    }
    private static IndustrialFurnaceBlockEntity machine(GameTestHelper h, FurnaceTier tier) {
        for (var cell : FurnaceLayout.cells(tier)) {
            BlockState state = block(tier).defaultBlockState().setValue(IndustrialFurnaceBlock.FACING, FACING)
                    .setValue(IndustrialFurnaceBlock.COLUMN, cell.column()).setValue(IndustrialFurnaceBlock.LAYER, cell.layer())
                    .setValue(IndustrialFurnaceBlock.ROW, cell.row());
            h.setBlock(FurnaceLayout.position(POS, FACING, cell), state);
        }
        IndustrialFurnaceBlockEntity entity = h.getBlockEntity(POS);
        entity.setOwner(OWNER, "furnace_verifier");
        return entity;
    }
    private static IndustrialFurnaceBlockEntity charged(GameTestHelper h, FurnaceTier tier) {
        var entity = machine(h, tier); entity.energyPort().insert(Long.MAX_VALUE, false); return entity;
    }
    private static void ticks(IndustrialFurnaceBlockEntity entity, int count) { for (int i = 0; i < count; i++) entity.serverTick(); }
    private static void input(IndustrialFurnaceBlockEntity entity, Item item, int count) { entity.input().setStackInSlot(0, new ItemStack(item, count)); }
    private static long items(net.neoforged.neoforge.items.IItemHandler inventory, Item item) {
        long count = 0; for (int i = 0; i < inventory.getSlots(); i++) if (inventory.getStackInSlot(i).is(item)) count += inventory.getStackInSlot(i).getCount(); return count;
    }
    private static List<FurnaceJob> jobs(IndustrialFurnaceBlockEntity entity) { return entity.jobs().stream().filter(Objects::nonNull).toList(); }
    private static int progress(IndustrialFurnaceBlockEntity entity) { return jobs(entity).stream().mapToInt(job -> job.progress).sum(); }
    private static CompoundTag save(GameTestHelper h, IndustrialFurnaceBlockEntity entity) { return entity.saveWithoutMetadata(h.getLevel().registryAccess()); }
    private static IndustrialFurnaceBlockEntity copy(GameTestHelper h, IndustrialFurnaceBlockEntity entity) {
        var copy = new IndustrialFurnaceBlockEntity(entity.getBlockPos(), entity.getBlockState());
        copy.loadWithComponents(save(h, entity), h.getLevel().registryAccess()); return copy;
    }
    private static void check(GameTestHelper h, boolean condition, String message) { h.assertTrue(condition, message); }
    private static ItemPort port(GameTestHelper h, FurnaceTier tier, boolean input) {
        var p = input ? FurnaceLayout.input(tier, FACING) : FurnaceLayout.output(tier, FACING);
        return h.getLevel().getCapability(ItemApi.BLOCK, h.absolutePos(FurnaceLayout.position(POS, FACING, p.cell())), p.face());
    }
    private static void fillOutput(IndustrialFurnaceBlockEntity entity) { for (int i = 0; i < entity.output().getSlots(); i++) entity.output().setStackInSlot(i, new ItemStack(Items.DIRT, 64)); }
    private static List<ProductionReceipt> receipts(GameTestHelper h, IndustrialFurnaceBlockEntity entity) {
        List<ProductionReceipt> received = new ArrayList<>();
        try (var subscription = DashboardAPI.production(h.getLevel().getServer()).subscribe(received::add)) {
            input(entity, Items.RAW_IRON, 1); ticks(entity, 700);
        }
        return received;
    }
    private static void placement(GameTestHelper h, FurnaceTier tier) {
        h.setBlock(POS.below(), Blocks.STONE);
        var placer = player(h); var stack = new ItemStack(block(tier));
        var context = new BlockPlaceContext(h.getLevel(), placer, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(h.absolutePos(POS.below()).getCenter().add(0, .5, 0), Direction.UP, h.absolutePos(POS.below()), false));
        var result = ((BlockItem) stack.getItem()).place(context);
        check(h, result.consumesAction() && stack.isEmpty(), "Successful placement must consume exactly one item");
        var state = h.getBlockState(POS);
        check(h, state.is(block(tier)), "Master absent");
        for (var cell : FurnaceLayout.cells(tier)) {
            var cellPos = FurnaceLayout.position(POS, state.getValue(IndustrialFurnaceBlock.FACING), cell);
            check(h, h.getBlockState(cellPos).is(block(tier)), "Missing footprint cell " + cell);
            check(h, cell.equals(FurnaceLayout.MASTER) == (h.getLevel().getBlockEntity(h.absolutePos(cellPos)) instanceof IndustrialFurnaceBlockEntity), "Exactly one master BE required");
        }
        h.succeed();
    }

    @GameTest(template="empty") public static void placementTierI(GameTestHelper h) { placement(h, FurnaceTier.I); }
    @GameTest(template="empty") public static void placementTierII(GameTestHelper h) { placement(h, FurnaceTier.II); }
    @GameTest(template="empty") public static void placementTierIII(GameTestHelper h) { placement(h, FurnaceTier.III); }
    @GameTest(template="empty") public static void invalidPlacementConservesItem(GameTestHelper h) {
        h.setBlock(POS.below(), Blocks.STONE); h.setBlock(POS.east(), Blocks.BEDROCK);
        var stack = new ItemStack(block(FurnaceTier.II));
        var context = new BlockPlaceContext(h.getLevel(), player(h), InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(h.absolutePos(POS.below()).getCenter().add(0, .5, 0), Direction.UP, h.absolutePos(POS.below()), false));
        ((BlockItem) stack.getItem()).place(context);
        check(h, stack.getCount() == 1 && !h.getBlockState(POS).is(block(FurnaceTier.II)), "Rejected multiblock placement consumed item or left master"); h.succeed();
    }
    @GameTest(template="empty") public static void breakingPartDismantlesOnce(GameTestHelper h) {
        var entity = charged(h, FurnaceTier.III); input(entity, Items.RAW_IRON, 5); ticks(entity, 20);
        h.getLevel().removeBlock(h.absolutePos(POS.east()), false);
        for (var cell : FurnaceLayout.cells(FurnaceTier.III)) check(h, !h.getBlockState(FurnaceLayout.position(POS,FACING,cell)).is(block(FurnaceTier.III)), "Orphan structure part");
        long dropped = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(entity.getBlockPos()).inflate(4)).stream().filter(e -> e.getItem().is(Items.RAW_IRON)).mapToInt(e -> e.getItem().getCount()).sum();
        check(h, dropped == 5, "Reservations/input must drop once: " + dropped); h.succeed();
    }
    @GameTest(template="empty") public static void survivalMachineDropOnce(GameTestHelper h) {
        var entity = machine(h,FurnaceTier.II); var pos=entity.getBlockPos().east(); var state=h.getLevel().getBlockState(pos);
        state.getBlock().playerWillDestroy(h.getLevel(),pos,state,player(h)); h.getLevel().removeBlock(pos,false);
        long count=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(entity.getBlockPos()).inflate(4)).stream().filter(e->e.getItem().is(block(FurnaceTier.II).asItem())).mapToInt(e->e.getItem().getCount()).sum();
        check(h,count==1,"Survival must drop one machine, got "+count);h.succeed();
    }
    @GameTest(template="empty") public static void creativeMachineNoDrop(GameTestHelper h) {
        var entity=machine(h,FurnaceTier.II); var placer=player(h);placer.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        check(h,placer.isCreative(),"Fixture player must actually be creative");
        var pos=entity.getBlockPos().east();var state=h.getLevel().getBlockState(pos);state.getBlock().playerWillDestroy(h.getLevel(),pos,state,placer);h.getLevel().removeBlock(pos,false);
        check(h,h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(entity.getBlockPos()).inflate(4)).stream().noneMatch(e->e.getItem().is(block(FurnaceTier.II).asItem())),"Creative dropped machine");placer.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);h.succeed();
    }
    @GameTest(template="empty") public static void noFuelSlot(GameTestHelper h) {
        var entity=machine(h,FurnaceTier.I);var menu=new FurnaceMenu(1,player(h).getInventory(),entity);
        check(h,menu.slots.size()==9+9+36,"Unexpected extra fuel slot");h.succeed();
    }
    @GameTest(template="empty") public static void coalDoesNotProgress(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.COAL,32);ticks(e,600);check(h,e.processedTotal()==0&&items(e.input(),Items.COAL)==32,"Coal consumed as fuel");h.succeed();}
    @GameTest(template="empty") public static void heAloneProgresses(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,450);check(h,items(e.output(),Items.IRON_INGOT)==1,"HE-only recipe failed");h.succeed();}
    @GameTest(template="empty") public static void noPowerPausesExactly(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,150);int before=progress(e);check(h,before>0,"Fixture did not begin processing");e.energyPort().setStored(0);ticks(e,80);check(h,progress(e)==before&&e.processedTotal()==0,"No-power changed exact progress");h.succeed();}
    @GameTest(template="empty") public static void restoreHeResumes(GameTestHelper h) {var e=machine(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,80);e.energyPort().insert(1000,false);ticks(e,400);check(h,items(e.output(),Items.IRON_INGOT)==1,"Restore did not resume");h.succeed();}
    @GameTest(template="empty") public static void offPausesJobs(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,2);ticks(e,140);int before=progress(e);e.setEnabled(false);ticks(e,60);check(h,progress(e)==before&&e.status()==FurnaceStatus.SWITCHED_OFF,"OFF changed progress");h.succeed();}
    @GameTest(template="empty") public static void onResumesJobs(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,130);e.setEnabled(false);ticks(e,20);e.setEnabled(true);ticks(e,500);check(h,e.processedTotal()==1,"ON failed to resume");h.succeed();}
    @GameTest(template="empty") public static void redstoneRequireSignal(GameTestHelper h) {var e=charged(h,FurnaceTier.I);e.setRedstoneMode(RedstoneMode.REQUIRE_SIGNAL);input(e,Items.RAW_IRON,1);ticks(e,400);check(h,progress(e)==0,"Missing signal bypass");h.setBlock(POS.west(),Blocks.REDSTONE_BLOCK);ticks(e,400);check(h,e.processedTotal()==1,"Signal did not enable");h.succeed();}
    @GameTest(template="empty") public static void redstoneRequireNoSignal(GameTestHelper h) {var e=charged(h,FurnaceTier.I);e.setRedstoneMode(RedstoneMode.REQUIRE_NO_SIGNAL);h.setBlock(POS.west(),Blocks.REDSTONE_BLOCK);input(e,Items.RAW_IRON,1);ticks(e,400);check(h,progress(e)==0,"Powered no-signal bypass");h.setBlock(POS.west(),Blocks.AIR);ticks(e,400);check(h,e.processedTotal()==1,"No signal did not resume");h.succeed();}
    @GameTest(template="empty") public static void realSmeltingRecipe(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.SAND,2);ticks(e,450);check(h,items(e.output(),Items.GLASS)==2,"Server SMELTING glass recipe failed");h.succeed();}
    @GameTest(template="empty") public static void invalidInputNotConsumed(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.DIAMOND,4);ticks(e,500);check(h,items(e.input(),Items.DIAMOND)==4&&e.processedTotal()==0,"Invalid input consumed");h.succeed();}
    @GameTest(template="empty") public static void invalidRecipeReservationRefunded(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,20);e.setEnabled(false);
        var id=jobs(e).getFirst().recipeId;var manager=h.getLevel().getRecipeManager();var original=List.copyOf(manager.getRecipes());
        try {
            manager.replaceRecipes(original.stream().filter(holder->!holder.id().equals(id)).toList());
            ticks(e,40);check(h,items(e.input(),Items.RAW_IRON)==1&&e.processedTotal()==0&&jobs(e).isEmpty(),"Removed server recipe did not refund once");h.succeed();
        } finally {manager.replaceRecipes(original);}
    }
    @GameTest(template="empty") public static void parallelLanes(GameTestHelper h) {var e=charged(h,FurnaceTier.III);input(e,Items.RAW_IRON,8);ticks(e,30);check(h,jobs(e).size()==8,"Tier III did not reserve 8 simultaneous jobs");h.succeed();}
    @GameTest(template="empty") public static void laneLimitPerTier(GameTestHelper h) {var e=charged(h,FurnaceTier.II);input(e,Items.RAW_IRON,64);ticks(e,30);check(h,jobs(e).size()==4&&items(e.input(),Items.RAW_IRON)==60,"Tier II lane limit bypass");h.succeed();}
    @GameTest(template="empty") public static void fullOutputPreservesPending(GameTestHelper h) {var e=charged(h,FurnaceTier.I);fillOutput(e);input(e,Items.RAW_IRON,1);ticks(e,500);check(h,e.pendingOutputs()==1&&jobs(e).getFirst().pending.is(Items.IRON_INGOT),"Full output lost finished result");h.succeed();}
    @GameTest(template="empty") public static void outputFreedTransfersPending(GameTestHelper h) {var e=charged(h,FurnaceTier.I);fillOutput(e);input(e,Items.RAW_IRON,1);ticks(e,450);e.output().setStackInSlot(0,ItemStack.EMPTY);ticks(e,20);check(h,e.pendingOutputs()==0&&items(e.output(),Items.IRON_INGOT)==1,"Pending output not transferred");h.succeed();}
    @GameTest(template="empty") public static void itemInputRejectsExtraction(GameTestHelper h) {var e=machine(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);var p=port(h,FurnaceTier.I,true);check(h,p!=null&&p.type()==ItemPortType.INPUT&&p.extractItem(0,1,false).isEmpty()&&items(e.input(),Items.RAW_IRON)==1,"Input extraction allowed");h.succeed();}
    @GameTest(template="empty") public static void itemOutputRejectsInsertion(GameTestHelper h) {var e=machine(h,FurnaceTier.I);var p=port(h,FurnaceTier.I,false);check(h,p!=null&&p.type()==ItemPortType.OUTPUT&&p.insertItem(0,new ItemStack(Items.IRON_INGOT),false).getCount()==1&&items(e.output(),Items.IRON_INGOT)==0,"Output insertion allowed");h.succeed();}
    @GameTest(template="empty") public static void itemHandlerFacesMatch(GameTestHelper h) {machine(h,FurnaceTier.III);for(var cell:FurnaceLayout.cells(FurnaceTier.III))for(var face:Direction.values()){var pos=h.absolutePos(FurnaceLayout.position(POS,FACING,cell));var item=h.getLevel().getCapability(ItemApi.BLOCK,pos,face);var standard=h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,pos,face);check(h,(item==null)==(standard==null),"ItemApi/NeoForge sides diverge");}h.succeed();}
    @GameTest(template="empty") public static void energyPortConsumerInput(GameTestHelper h) {var e=machine(h,FurnaceTier.II);var layout=FurnaceLayout.energy(FurnaceTier.II,FACING);var p=h.getLevel().getCapability(EnergyApi.BLOCK,h.absolutePos(FurnaceLayout.position(POS,FACING,layout.cell())),layout.face());check(h,p!=null&&p.role()==EnergyRole.CONSUMER&&p.type()==EnergyPortType.INPUT&&p.extract(100,false)==0,"Wrong HE port contract");h.succeed();}
    @GameTest(template="empty") public static void energySimulationExact(GameTestHelper h) {var e=machine(h,FurnaceTier.I);var p=e.energyPort();long simulated=p.insert(3000,true);check(h,simulated==2000&&p.stored()==0&&p.insert(simulated,false)==simulated&&p.stored()==2000,"Simulation mutated or over-accepted");h.succeed();}
    @GameTest(template="empty") public static void noDuplicateHe(GameTestHelper h) {var e=machine(h,FurnaceTier.I);var p=e.energyPort();check(h,p.insert(500,false)==500&&p.insert(-20,false)==0&&p.extract(Long.MAX_VALUE,false)==0&&p.stored()==500,"HE duplicated/extracted");h.succeed();}
    @GameTest(template="empty") public static void longSeriesConservesHe(GameTestHelper h) {var e=charged(h,FurnaceTier.III);long before=e.energyStored();input(e,Items.RAW_IRON,64);ticks(e,1050);check(h,e.processedTotal()==64,"Long series operations");long spent=before-e.energyStored();check(h,spent>=64*100+800&&spent<=64*100+800+100,"Long series operation cost drift: "+spent);h.succeed();}
    @GameTest(template="empty") public static void activeJobReload(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,2);ticks(e,160);int before=progress(e);var data=save(h,e);e.loadWithComponents(data,h.getLevel().registryAccess());check(h,progress(e)==before&&jobs(e).size()==2,"Active jobs changed on reload");ticks(e,350);check(h,e.processedTotal()==2&&items(e.output(),Items.IRON_INGOT)==2,"Reloaded jobs did not complete exactly once");h.succeed();}
    @GameTest(template="empty") public static void pendingOutputReload(GameTestHelper h) {var e=charged(h,FurnaceTier.I);fillOutput(e);input(e,Items.RAW_IRON,1);ticks(e,450);var data=save(h,e);e.loadWithComponents(data,h.getLevel().registryAccess());check(h,e.pendingOutputs()==1&&jobs(e).getFirst().pending.is(Items.IRON_INGOT),"Pending result reload lost");e.output().setStackInSlot(0,ItemStack.EMPTY);ticks(e,40);check(h,e.processedTotal()==1&&items(e.output(),Items.IRON_INGOT)==1,"Reloaded pending output duplicated/lost");h.succeed();}
    @GameTest(template="empty") public static void heReload(GameTestHelper h) {var e=machine(h,FurnaceTier.I);e.energyPort().insert(731,false);check(h,copy(h,e).energyStored()==731,"HE save/load changed charge");h.succeed();}
    @GameTest(template="empty") public static void xpReload(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,2);ticks(e,450);check(h,e.storedXp()>0&&copy(h,e).storedXp()==e.storedXp(),"XP reload changed");h.succeed();}
    @GameTest(template="empty") public static void noOfflineCatchup(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,150);var c=copy(h,e);check(h,progress(c)==progress(e)&&c.processedTotal()==e.processedTotal(),"Load advanced offline work");h.succeed();}
    @GameTest(template="empty") public static void productionReceiptOnce(GameTestHelper h) {var e=charged(h,FurnaceTier.I);fillOutput(e);var received=receipts(h,e);check(h,received.size()==1&&e.pendingOutputs()==1,"Receipt missing or repeated while pending");h.succeed();}
    @GameTest(template="empty") public static void receiptActualQuantity(GameTestHelper h) {var r=receipts(h,charged(h,FurnaceTier.I));check(h,r.size()==1&&r.getFirst().quantity()==1&&r.getFirst().result().is(Items.IRON_INGOT),"Wrong actual receipt result");h.succeed();}
    @GameTest(template="empty") public static void receiptActualRecipeId(GameTestHelper h) {var r=receipts(h,charged(h,FurnaceTier.I));check(h,r.size()==1&&r.getFirst().recipe().equals(Optional.of(ResourceLocation.withDefaultNamespace("iron_ingot_from_smelting_raw_iron"))),"Wrong receipt recipe ID");h.succeed();}
    @GameTest(template="empty") public static void transactionStable(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,120);check(h,jobs(copy(h,e)).getFirst().transactionId.equals(jobs(e).getFirst().transactionId),"Transaction ID changed");h.succeed();}
    @GameTest(template="empty") public static void receiptNetwork(GameTestHelper h) {var e=charged(h,FurnaceTier.I);var network=DashboardAPI.networks(h.getLevel().getServer()).createNetwork("Furnace receipt",OWNER);try{e.setHomeNetwork(network.id(),network.name());var r=receipts(h,e);check(h,r.size()==1&&r.getFirst().network().equals(Optional.of(network.id())),"Receipt missing network");}finally{DashboardAPI.networks(h.getLevel().getServer()).deleteNetwork(network.id());}h.succeed();}
    @GameTest(template="empty") public static void productionStartPersists(GameTestHelper h) {var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,20);var job=jobs(e).getFirst();check(h,job.start!=null&&job.start.equals(jobs(copy(h,e)).getFirst().start),"ProductionStart epoch/ordinal changed");h.succeed();}
    @GameTest(template="empty") public static void unknownOwnerNoFakeReceipt(GameTestHelper h) {var e=charged(h,FurnaceTier.I);var data=save(h,e);data.remove("owner");e.loadWithComponents(data,h.getLevel().registryAccess());check(h,e.owner().isEmpty(),"Owner fixture not cleared");var r=receipts(h,e);check(h,r.isEmpty()&&e.processedTotal()==1,"Unknown owner generated fictitious receipt");h.succeed();}
    @GameTest(template="empty") public static void homecoreDeviceRegistered(GameTestHelper h) {var e=machine(h,FurnaceTier.I);ticks(e,1);check(h,DashboardAPI.devices(h.getLevel().getServer()).get(e.deviceId()).filter(d->d.deviceType().equals(FurnaceDevice.TYPE)).isPresent(),"HomeCore provider not registered");h.succeed();}
    @GameTest(template="empty") public static void metricsUpdate(GameTestHelper h) {var e=charged(h,FurnaceTier.II);ticks(e,1);var d=DashboardAPI.devices(h.getLevel().getServer()).get(e.deviceId()).orElseThrow();check(h,d.metrics().size()==17&&d.metrics().stream().anyMatch(m->m.id().getPath().equals("energy_stored")&&m.value().equals(6000L)),"Metrics missing/stale");h.succeed();}
    @GameTest(template="empty") public static void switchableOffControllable(GameTestHelper h) {var e=charged(h,FurnaceTier.I);ticks(e,1);var d=DashboardAPI.devices(h.getLevel().getServer()).get(e.deviceId()).orElseThrow();check(h,d instanceof Switchable&&((Switchable)d).setPowered(false).isSuccess()&&!e.enabled()&&d.status().state()==DeviceStatus.State.DISABLED&&((Switchable)d).setPowered(true).isSuccess(),"Switchable OFF/ON failed");h.succeed();}
    @GameTest(template="empty") public static void renamablePersists(GameTestHelper h) {var e=machine(h,FurnaceTier.I);ticks(e,1);var d=DashboardAPI.devices(h.getLevel().getServer()).get(e.deviceId()).orElseThrow();check(h,d instanceof Renamable&&((Renamable)d).rename("Atelier").isSuccess()&&d.displayName().getString().equals("Atelier")&&copy(h,e).displayName().getString().equals("Atelier"),"Renamable save/load failed");h.succeed();}
    @GameTest(template="empty") public static void networkBinding(GameTestHelper h) {var e=machine(h,FurnaceTier.I);ticks(e,1);var server=h.getLevel().getServer();var network=DashboardAPI.networks(server).createNetwork("Furnace binding",OWNER);try{var d=DashboardAPI.devices(server).get(e.deviceId()).orElseThrow();check(h,DashboardAPI.bindDevice(player(h),d,Optional.of(network.id()))==NetworkMember.BindResult.BOUND&&e.homeNetworkId().equals(Optional.of(network.id())),"HomeCore network binding not recorded");}finally{DashboardAPI.networks(server).deleteNetwork(network.id());}h.succeed();}
    @GameTest(template="empty") public static void noForcedChunks(GameTestHelper h) {int before=h.getLevel().getForcedChunks().size();var e=charged(h,FurnaceTier.III);input(e,Items.RAW_IRON,2);ticks(e,600);check(h,h.getLevel().getForcedChunks().size()==before,"Furnace added forced chunk");h.succeed();}
    @GameTest(template="empty") public static void unloadedMasterNotMissingPart(GameTestHelper h) {
        // Query a real part across an unloaded chunk boundary without loading its master.
        var absolute=h.absolutePos(POS);int farChunkX=(absolute.getX()>>4)+512;
        BlockPos part=new BlockPos((farChunkX<<4)-1,absolute.getY(),absolute.getZ());
        check(h,!h.getLevel().hasChunkAt(part.east()),"Boundary master chunk must start unloaded");h.getLevel().getChunkAt(part);
        check(h,!h.getLevel().hasChunkAt(part.east()),"Generating part chunk loaded its neighbor before the test");
        var state=block(FurnaceTier.II).defaultBlockState().setValue(IndustrialFurnaceBlock.FACING,Direction.SOUTH).setValue(IndustrialFurnaceBlock.COLUMN,1);
        h.getLevel().setBlock(part,state,18);
        check(h,!h.getLevel().hasChunkAt(part.east()),"Fixture placement loaded neighbor before furnace tick");
        block(FurnaceTier.II).tick(state,h.getLevel(),part,h.getLevel().random);
        check(h,h.getLevel().getBlockState(part).equals(state)&&!h.getLevel().hasChunkAt(part.east()),"Unloaded master removed part or loaded chunk");h.getLevel().removeBlock(part,false);h.succeed();
    }

    @GameTest(template="empty") public static void runtimeSmeltingRecipeReportsMultipleResults(GameTestHelper h) {
        var manager=h.getLevel().getRecipeManager();var original=List.copyOf(manager.getRecipes());
        var recipeId=ResourceLocation.fromNamespaceAndPath(FurnaceValidation.MOD_ID,"three_ingots");
        var recipes=new ArrayList<net.minecraft.world.item.crafting.RecipeHolder<?>>(original);
        recipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(recipeId,new net.minecraft.world.item.crafting.SmeltingRecipe(
                "",net.minecraft.world.item.crafting.CookingBookCategory.MISC,net.minecraft.world.item.crafting.Ingredient.of(Items.POISONOUS_POTATO),new ItemStack(Items.IRON_INGOT,3),.25f,7)));
        List<ProductionReceipt> received=new ArrayList<>();
        try(var subscription=DashboardAPI.production(h.getLevel().getServer()).subscribe(received::add)) {
            manager.replaceRecipes(recipes);var e=charged(h,FurnaceTier.I);input(e,Items.POISONOUS_POTATO,1);ticks(e,140);
            check(h,items(e.output(),Items.IRON_INGOT)==3&&received.size()==1&&received.getFirst().quantity()==3&&received.getFirst().recipe().equals(Optional.of(recipeId)),"Dynamic server SMELTING recipe result/receipt incorrect");
        }finally{manager.replaceRecipes(original);}h.succeed();
    }

    @GameTest(template="empty") public static void hiddenTabsRejectSlotClicks(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);e.output().setStackInSlot(0,new ItemStack(Items.IRON_INGOT));var p=player(h);
        var menu=new FurnaceMenu(4,p.getInventory(),e);menu.setClientTab(1);
        menu.clicked(e.input().getSlots(),0,net.minecraft.world.inventory.ClickType.PICKUP,p);
        check(h,menu.slots.stream().noneMatch(net.minecraft.world.inventory.Slot::isActive)&&menu.getCarried().isEmpty()&&items(e.output(),Items.IRON_INGOT)==1,"Hidden tab retained clickable slots");h.succeed();
    }

    @GameTest(template="empty") public static void xpCollectionIsServerAuthorizedAndSingleUse(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,2);ticks(e,450);var p=player(h);int before=p.totalExperience;double stored=e.storedXp();
        var stranger=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.fromString("36d47c5a-f563-4e82-aed2-07e639c2c0a6"),"furnace_stranger"));
        e.collectXp(stranger);check(h,e.storedXp()==stored,"Unauthorized player collected XP");
        e.collectXp(p);int granted=p.totalExperience-before;e.collectXp(p);
        check(h,granted==(int)Math.floor(stored)&&p.totalExperience-before==granted&&e.storedXp()<1,"XP collection duplicated or lost remainder");h.succeed();
    }

    @GameTest(template="empty") public static void machineRecipesLoadWithActualHomecoreComponents(GameTestHelper h) {
        for(var tier:FurnaceTier.values()) {
            var recipe=h.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("homelink_furnace",tier.id()));
            check(h,recipe.isPresent()&&recipe.get().value().getResultItem(h.getLevel().registryAccess()).is(block(tier).asItem()),"Machine recipe missing or invalid HomeCore ingredient: "+tier);
            check(h,recipe.get().value().getIngredients().stream().anyMatch(ingredient->ingredient.getItems().length>0&&net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(ingredient.getItems()[0].getItem()).getNamespace().equals("homecore")),"No actual HomeCore component ingredient");
        }h.succeed();
    }

    @GameTest(template="empty") public static void tenIronOperationsPreserveSevenExactXpAcrossReload(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,10);ticks(e,1300);
        check(h,e.processedTotal()==10&&e.storedXp()==7.0,"Ten 0.7-XP operations must total exactly 7 XP");
        e.loadWithComponents(save(h,e),h.getLevel().registryAccess());check(h,e.storedXp()==7.0,"Exact XP did not persist");
        var p=player(h);int before=p.totalExperience;e.collectXp(p);e.collectXp(p);
        check(h,p.totalExperience-before==7&&e.storedXp()==0,"Reloaded XP was lost or collected twice");h.succeed();
    }

    @GameTest(template="empty") public static void normalProductionsNeverPublishOutputFullEvents(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);List<fr.lkdm.homecore.api.event.DeviceEvent> events=new ArrayList<>();
        try(var subscription=DashboardAPI.events(h.getLevel().getServer()).subscribe(event->{if(event.source().equals(e.deviceId()))events.add(event);})) {
            input(e,Items.RAW_IRON,5);ticks(e,1000);
            check(h,e.processedTotal()==5,"Event fixture did not complete actual production");
            check(h,events.stream().noneMatch(event->event.type().equals(FurnaceDevice.id("furnace_output_full"))||event.type().equals(FurnaceDevice.id("furnace_output_recovered"))),"Normal item completion emitted output-blocked events");
        }h.succeed();
    }

    @GameTest(template="empty") public static void noForgeEnergyCapabilityOnAnyFace(GameTestHelper h) {
        var e=machine(h,FurnaceTier.I);for(var face:Direction.values())
            check(h,h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,e.getBlockPos(),face)==null,"Unexpected FE compatibility on "+face);
        e.setEnabled(false);check(h,e.energyPort().insert(80,false)==80&&e.energyStored()==80,"OFF must remain HE-chargeable");h.succeed();
    }

    @GameTest(template="empty") public static void cachedPortsRefuseTransfersAfterMasterRemoval(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);e.output().setStackInSlot(0,new ItemStack(Items.IRON_INGOT,2));
        var in=port(h,FurnaceTier.I,true);var out=port(h,FurnaceTier.I,false);
        var he=h.getLevel().getCapability(EnergyApi.BLOCK,e.getBlockPos(),Direction.EAST);
        check(h,in!=null&&out!=null&&he!=null,"Missing cached fixture ports");
        e.setRemoved();
        check(h,in.insertItem(0,new ItemStack(Items.RAW_IRON),false).getCount()==1&&items(e.input(),Items.RAW_IRON)==1,"Cached input mutated removed inventory");
        check(h,out.extractItem(0,2,false).isEmpty()&&items(e.output(),Items.IRON_INGOT)==2,"Cached output extracted from removed machine");
        check(h,he.insert(100,false)==0&&he.stored()==0&&he.capacity()==0,"Cached HE port accepted energy while unloaded");
        e.clearRemoved();e.serverTick();h.succeed();
    }

    @GameTest(template="empty") public static void malformedXpExponentCannotCrashChunkLoad(GameTestHelper h) {
        var e=machine(h,FurnaceTier.I);var tag=save(h,e);tag.putString("xpExact","1E-2147483648");tag.putDouble("xp",.75);
        e.loadWithComponents(tag,h.getLevel().registryAccess());
        check(h,e.storedXp()==.75,"Malformed exact XP must use bounded legacy fallback");h.succeed();
    }

    private static long events(List<fr.lkdm.homecore.api.event.DeviceEvent> received,String path) {
        return received.stream().filter(event->event.type().equals(FurnaceDevice.id(path))).count();
    }
    @GameTest(template="empty") public static void offDoesNotPretendBlockedOutputRecovered(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);fillOutput(e);List<fr.lkdm.homecore.api.event.DeviceEvent> received=new ArrayList<>();
        try(var subscription=DashboardAPI.events(h.getLevel().getServer()).subscribe(event->{if(event.source().equals(e.deviceId()))received.add(event);})) {
            input(e,Items.RAW_IRON,1);ticks(e,450);check(h,events(received,"furnace_output_full")==1,"Output warning fixture missing");
            e.setEnabled(false);ticks(e,10);e.setEnabled(true);ticks(e,10);
            check(h,events(received,"furnace_output_recovered")==0&&e.pendingOutputs()==1,"OFF/ON falsely announced output recovery");
            e.output().setStackInSlot(0,ItemStack.EMPTY);ticks(e,20);
            check(h,events(received,"furnace_output_recovered")==1,"Real output transfer must recover once");
        }h.succeed();
    }
    @GameTest(template="empty") public static void offDoesNotPretendRemovedRecipeRecovered(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);var manager=h.getLevel().getRecipeManager();var original=List.copyOf(manager.getRecipes());
        List<fr.lkdm.homecore.api.event.DeviceEvent> received=new ArrayList<>();
        try(var subscription=DashboardAPI.events(h.getLevel().getServer()).subscribe(event->{if(event.source().equals(e.deviceId()))received.add(event);})) {
            input(e,Items.RAW_IRON,1);ticks(e,20);var id=jobs(e).getFirst().recipeId;
            manager.replaceRecipes(original.stream().filter(holder->!holder.id().equals(id)).toList());ticks(e,20);
            check(h,e.recipeInvalid()&&events(received,"furnace_recipe_invalid")==1,"Removed recipe warning fixture missing");
            e.setEnabled(false);ticks(e,10);e.setEnabled(true);ticks(e,10);
            check(h,events(received,"furnace_recipe_restored")==0,"OFF/ON falsely announced recipe recovery");
            manager.replaceRecipes(original);ticks(e,20);
            check(h,!e.recipeInvalid()&&events(received,"furnace_recipe_restored")==1,"Real recipe restoration must announce once");
        }finally{manager.replaceRecipes(original);}h.succeed();
    }
    @GameTest(template="empty") public static void offDoesNotPretendMissingHeRecovered(GameTestHelper h) {
        var e=machine(h,FurnaceTier.I);List<fr.lkdm.homecore.api.event.DeviceEvent> received=new ArrayList<>();
        try(var subscription=DashboardAPI.events(h.getLevel().getServer()).subscribe(event->{if(event.source().equals(e.deviceId()))received.add(event);})) {
            input(e,Items.RAW_IRON,1);ticks(e,30);check(h,events(received,"furnace_no_power")==1,"No-power warning fixture missing");
            e.setEnabled(false);ticks(e,10);e.setEnabled(true);ticks(e,10);
            check(h,events(received,"furnace_power_restored")==0&&e.energyStored()==0,"OFF/ON falsely announced HE recovery");
            e.energyPort().insert(1000,false);ticks(e,20);
            check(h,events(received,"furnace_power_restored")==1,"Actual HE supply must restore once");
        }h.succeed();
    }

    @GameTest(template="empty") public static void coolingFinalTickMarksChunkDirtyAndPersistsCold(GameTestHelper h) {
        var e=machine(h,FurnaceTier.I);e.setEnabled(false);ticks(e,1);
        var heat=new CompoundTag();heat.putInt("progress",1);heat.putInt("hold",0);heat.putString("state","COOLING");
        e.heat().load(heat,fr.lkdm.homelink.furnace.config.FurnaceServerConfig.settings(e.tier()));
        var chunk=h.getLevel().getChunkAt(e.getBlockPos());chunk.setUnsaved(false);ticks(e,1);
        check(h,e.heat().progress==0&&e.heat().state()==FurnaceHeat.State.COLD&&chunk.isUnsaved(),"Final cooling tick did not mark changed chunk");
        check(h,copy(h,e).heat().progress==0&&copy(h,e).heat().state()==FurnaceHeat.State.COLD,"Cold state did not persist");h.succeed();
    }
    @GameTest(template="empty") public static void satelliteRemovalPersistsRequestWithoutLoadingMaster(GameTestHelper h) {
        var absolute=h.absolutePos(POS);int farChunkX=(absolute.getX()>>4)+768;
        var part=new BlockPos((farChunkX<<4)-1,absolute.getY(),absolute.getZ());var master=part.east();
        check(h,!h.getLevel().hasChunkAt(master),"Request fixture master starts unloaded");h.getLevel().getChunkAt(part);
        check(h,!h.getLevel().hasChunkAt(master),"Part generation loaded neighboring master");
        var state=block(FurnaceTier.II).defaultBlockState().setValue(IndustrialFurnaceBlock.FACING,Direction.SOUTH).setValue(IndustrialFurnaceBlock.COLUMN,1);
        h.getLevel().setBlock(part,state,18);h.getLevel().setBlock(part,Blocks.AIR.defaultBlockState(),18);
        check(h,!h.getLevel().hasChunkAt(master),"Removing satellite forced master chunk");
        var expected=state.setValue(IndustrialFurnaceBlock.COLUMN,0);var queue=FurnaceDismantleRequests.get(h.getLevel());
        var restored=FurnaceDismantleRequests.load(queue.save(new CompoundTag(),h.getLevel().registryAccess()));
        check(h,restored.take(master,expected).filter(FurnaceDismantleRequests.Request::dropMachine).isPresent(),"Confirmed removal request lost in SavedData");
        check(h,restored.take(master,expected).isEmpty(),"Removal request consumed twice");queue.take(master,expected);h.succeed();
    }
    private static long drops(GameTestHelper h,BlockPos pos,Item item) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(4)).stream().filter(entity->entity.getItem().is(item)).mapToInt(entity->entity.getItem().getCount()).sum();
    }
    @GameTest(template="empty") public static void reloadedDismantleRequestReturnsReservationsAndMachineOnce(GameTestHelper h) {
        var e=charged(h,FurnaceTier.II);input(e,Items.RAW_IRON,5);ticks(e,20);var state=e.getBlockState();var pos=e.getBlockPos();
        var queue=FurnaceDismantleRequests.get(h.getLevel());queue.request(pos,state,true);
        var restored=FurnaceDismantleRequests.load(queue.save(new CompoundTag(),h.getLevel().registryAccess()));
        h.getLevel().getDataStorage().set(FurnaceDismantleRequests.FILE_ID,restored);e.serverTick();e.serverTick();
        check(h,!h.getLevel().getBlockState(pos).is(block(FurnaceTier.II)),"Queued master was not dismantled on load");
        check(h,drops(h,pos,Items.RAW_IRON)==5&&drops(h,pos,block(FurnaceTier.II).asItem())==1,"Deferred dismantle lost/duplicated owned inventory or machine");
        check(h,restored.take(pos,state).isEmpty(),"Processed request survived removal");h.succeed();
    }
    @GameTest(template="empty") public static void deferredCreativeRequestNeverDropsMachine(GameTestHelper h) {
        var e=machine(h,FurnaceTier.II);var pos=e.getBlockPos();var queue=FurnaceDismantleRequests.get(h.getLevel());
        queue.request(pos,e.getBlockState(),false);queue.request(pos,e.getBlockState(),true);e.serverTick();
        check(h,drops(h,pos,block(FurnaceTier.II).asItem())==0,"Creative / wrong-tool request was overridden or dropped machine");h.succeed();
    }
    @GameTest(template="empty") public static void staleDismantleRequestCannotDestroyDifferentMachine(GameTestHelper h) {
        var e=machine(h,FurnaceTier.I);var queue=FurnaceDismantleRequests.get(h.getLevel());
        queue.request(e.getBlockPos(),block(FurnaceTier.II).defaultBlockState(),true);e.serverTick();
        check(h,h.getLevel().getBlockEntity(e.getBlockPos())==e&&queue.take(e.getBlockPos(),e.getBlockState()).isEmpty(),"Stale tier request destroyed replacement machine or remained queued");h.succeed();
    }
    @GameTest(template="empty") public static void datapackSyncRevalidatesBeforeNextCompletionTick(GameTestHelper h) {
        var e=charged(h,FurnaceTier.I);input(e,Items.RAW_IRON,1);ticks(e,150);var job=jobs(e).getFirst();
        long before=job.paidEnergy;job.progress=job.duration-1;job.paidEnergy=FurnaceEnergyCost.cumulativeCost(job.totalEnergy,job.duration,job.progress);
        e.energyPort().consume(job.paidEnergy-before);var manager=h.getLevel().getRecipeManager();var original=List.copyOf(manager.getRecipes());
        List<ProductionReceipt> received=new ArrayList<>();
        try(var subscription=DashboardAPI.production(h.getLevel().getServer()).subscribe(received::add)) {
            manager.replaceRecipes(original.stream().filter(holder->!holder.id().equals(job.recipeId)).toList());
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(h.getLevel().getServer().getPlayerList(),null));
            e.serverTick();check(h,e.processedTotal()==0&&items(e.output(),Items.IRON_INGOT)==0&&items(e.input(),Items.RAW_IRON)==1&&received.isEmpty(),"Reloaded recipe completed before immediate generation invalidation");
        }finally {
            manager.replaceRecipes(original);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.OnDatapackSyncEvent(h.getLevel().getServer().getPlayerList(),null));
        }h.succeed();
    }
    @GameTest(template="empty") public static void unconfirmedPlayerIntentIsTransientAndExpires(GameTestHelper h) {
        var queue=new FurnaceDismantleRequests();var master=h.absolutePos(POS);var part=master.east();var state=block(FurnaceTier.II).defaultBlockState();
        queue.recordPlayerIntent(part,false,10);
        check(h,FurnaceDismantleRequests.load(queue.save(new CompoundTag(),h.getLevel().registryAccess())).take(master,state).isEmpty(),"Unconfirmed creative click was persisted as destruction");
        queue.confirmRemoval(part,master,state,11);check(h,queue.take(master,state).filter(FurnaceDismantleRequests.Request::dropMachine).isPresent(),"Old creative intent incorrectly suppressed later nonplayer drop");
        queue.recordPlayerIntent(part,false,20);queue.confirmRemoval(part,master,state,20);
        check(h,queue.take(master,state).filter(request->!request.dropMachine()).isPresent(),"Same-tick creative removal lost its no-drop rule");h.succeed();
    }
    @GameTest(template="empty") public static void everyRedstoneModeLeavesAdjacentChunkUnloaded(GameTestHelper h) {
        var absolute=h.absolutePos(POS);int farChunkX=(absolute.getX()>>4)+1024;
        var pos=new BlockPos((farChunkX<<4)-1,absolute.getY(),absolute.getZ());
        check(h,!h.getLevel().hasChunkAt(pos.east()),"Redstone fixture neighboring chunk starts unloaded");h.getLevel().getChunkAt(pos);
        check(h,!h.getLevel().hasChunkAt(pos.east()),"Redstone fixture generation loaded neighbor");
        h.getLevel().setBlock(pos,block(FurnaceTier.I).defaultBlockState(),18);
        var e=(IndustrialFurnaceBlockEntity)h.getLevel().getBlockEntity(pos);check(h,e!=null,"No redstone border master");
        for(var mode:RedstoneMode.values()) {
            e.setRedstoneMode(mode);ticks(e,25);
            check(h,!h.getLevel().hasChunkAt(pos.east()),mode+" read loaded adjacent chunk");
        }h.succeed();
    }
}
