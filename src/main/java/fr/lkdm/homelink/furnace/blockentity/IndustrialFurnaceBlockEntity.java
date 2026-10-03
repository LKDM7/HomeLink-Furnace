package fr.lkdm.homelink.furnace.blockentity;

import fr.lkdm.homelink.furnace.HomeLinkFurnace;
import fr.lkdm.homelink.furnace.block.*;
import fr.lkdm.homelink.furnace.config.FurnaceServerConfig;
import fr.lkdm.homelink.furnace.furnace.*;
import fr.lkdm.homelink.furnace.homelink.*;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.energy.EnergyBuffer;
import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.energy.EnergyPortType;
import fr.lkdm.homecore.api.energy.EnergyRole;
import fr.lkdm.homecore.api.item.*;
import fr.lkdm.homecore.api.production.ProductionReceipt;
import fr.lkdm.homecore.api.security.Permission;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.*;

/** One authoritative master owns every reservation, result and energy debit. Server thread only. */
public final class IndustrialFurnaceBlockEntity extends BlockEntity {
    private UUID deviceId=UUID.randomUUID(),owner,network;
    private String customName="",ownerName="",networkName="";
    private boolean enabled=true, dismantled, playerBreak, inventoryDirty=true, structureComplete=true, structureLoaded=true, invalidRecipe;
    private RedstoneMode redstone=RedstoneMode.IGNORE;
    private FurnaceStatus status=FurnaceStatus.IDLE;
    private final FurnaceHeat heat=new FurnaceHeat();
    private final FurnaceScheduler scheduler=new FurnaceScheduler();
    private final ItemStackHandler input,output;
    private final ItemPort inputPort,outputPort;
    private final EnergyBuffer energy=new EnergyBuffer(()->FurnaceServerConfig.settings(tier()).energyBuffer(),this::setChanged);
    private final EnergyPort exposedEnergy=new EnergyPort(){
        @Override public EnergyRole role(){return EnergyRole.CONSUMER;}
        @Override public EnergyPortType type(){return EnergyPortType.INPUT;}
        @Override public long stored(){return portAvailable()?energy.stored():0;}
        @Override public long capacity(){return portAvailable()?energy.capacity():0;}
        @Override public long insert(long amount,boolean simulate){return portAvailable()?energy.insert(amount,simulate):0;}
        @Override public long extract(long amount,boolean simulate){return 0;}
    };
    private final List<FurnaceJob> jobs=new ArrayList<>();
    /** Refunds that cannot fit input; never dropped during normal operation or discarded. */
    private final List<ItemStack> recovery=new ArrayList<>();
    private static final int MAX_INVALIDATED_RECIPES=8;
    /** Diagnostic snapshots only; these do not own items and are never refunded or dropped. */
    private final Set<ResourceLocation> invalidatedRecipes=new LinkedHashSet<>();
    private final Map<ResourceLocation,ItemStack> invalidatedInputs=new HashMap<>();
    private RecipeManager lastRecipes;
    private long recipeGeneration=-1;
    private FurnaceDevice device;
    private long processed,draw;
    // Use the recipe float's canonical decimal value, e.g. .7f means exactly 0.7 XP.
    // Binary floating-point accumulation would otherwise pay only 6 XP for ten iron operations.
    private java.math.BigDecimal xp=java.math.BigDecimal.ZERO;
    private int ticks,clientTemperature;
    private FurnaceHeat.State clientHeat=FurnaceHeat.State.COLD;

    public IndustrialFurnaceBlockEntity(BlockPos pos,BlockState state){
        super(FurnaceRegistries.MASTER.get(),pos,state);
        input=new ItemStackHandler(tier().inputSlots()){
            @Override public boolean isItemValid(int slot,ItemStack stack){return level==null||level.isClientSide||resolve(stack).isPresent();}
            @Override protected void onContentsChanged(int slot){inventoryDirty=true;setChanged();}
        };
        output=new ItemStackHandler(tier().outputSlots()){@Override protected void onContentsChanged(int slot){inventoryDirty=true;setChanged();}};
        inputPort=ItemApi.of(liveInventory(input),ItemPortType.INPUT);outputPort=ItemApi.of(liveInventory(output),ItemPortType.OUTPUT);
        for(int i=0;i<tier().jobs();i++)jobs.add(null);
    }
    public FurnaceTier tier(){return getBlockState().getBlock() instanceof IndustrialFurnaceBlock b?b.tier():FurnaceTier.I;}
    public Direction facing(){return getBlockState().getValue(IndustrialFurnaceBlock.FACING);}
    public ItemStackHandler input(){return input;} public ItemStackHandler output(){return output;}
    public EnergyBuffer energyPort(){return energy;}
    public EnergyPort capabilityEnergyPort(){return exposedEnergy;}
    /** Cached logistics endpoints must become inert when their master unloads or is removed. */
    private boolean portAvailable(){return !isRemoved()&&!dismantled&&level instanceof ServerLevel&&level.hasChunkAt(worldPosition)&&level.getBlockEntity(worldPosition)==this;}
    private IItemHandler liveInventory(ItemStackHandler inventory){return new IItemHandler(){
        @Override public int getSlots(){return inventory.getSlots();}
        @Override public ItemStack getStackInSlot(int slot){return portAvailable()?inventory.getStackInSlot(slot).copy():ItemStack.EMPTY;}
        @Override public ItemStack insertItem(int slot,ItemStack stack,boolean simulate){return portAvailable()?inventory.insertItem(slot,stack,simulate):stack;}
        @Override public ItemStack extractItem(int slot,int amount,boolean simulate){return portAvailable()?inventory.extractItem(slot,amount,simulate):ItemStack.EMPTY;}
        @Override public int getSlotLimit(int slot){return inventory.getSlotLimit(slot);}
        @Override public boolean isItemValid(int slot,ItemStack stack){return portAvailable()&&inventory.isItemValid(slot,stack);}
    };}
    public List<FurnaceJob> jobs(){return Collections.unmodifiableList(jobs);}
    public FurnaceHeat heat(){return heat;} public FurnaceStatus status(){return status;}
    public boolean recipeInvalid(){return invalidRecipe;}
    public boolean powerWaiting(){return activeJobs()>0&&!hasProcessingPower();}
    public boolean hasProcessingPower(){
        if(energy.stored()==0)return false;
        if(activeJobs()==0)return true;
        if(!heat.canProcess()){
            var cfg=FurnaceServerConfig.settings(tier());
            return energy.stored()>=Math.max(1,FurnaceEnergyCost.nextCost(cfg.warmupEnergy(),cfg.warmupTicks(),Math.min(heat.progress,cfg.warmupTicks())));
        }
        long due=0;for(var job:jobs)if(job!=null&&job.pending.isEmpty())due+=job.nextEnergy();
        return energy.stored()>=Math.max(1,due);
    }
    public UUID deviceId(){return deviceId;} public Optional<UUID> owner(){return Optional.ofNullable(owner);}
    public Optional<UUID> homeNetworkId(){return Optional.ofNullable(network);}
    public void setOwner(UUID value,String name){owner=value;ownerName=name;setChanged();}
    public void setHomeNetwork(UUID id,String name){network=id;networkName=name;setChanged();}
    public void clearHomeNetwork(){network=null;networkName="";setChanged();}
    public void resetIdentityAfterCollision(){deviceId=UUID.randomUUID();clearHomeNetwork();}
    public FurnaceDevice device(){return device;}
    public Component displayName(){return customName.isEmpty()?Component.translatable("block.homelink_furnace."+tier().id()):Component.literal(customName);}
    public String customName(){return customName;}
    public void setCustomName(String value){customName=value;setChanged();}
    public boolean enabled(){return enabled;}
    public void setEnabled(boolean value){enabled=value;inventoryDirty=true;setChanged();recomputeStatus();if(device!=null)device.refresh();}
    public RedstoneMode redstoneMode(){return redstone;}
    public void setRedstoneMode(RedstoneMode value){redstone=Objects.requireNonNull(value);setChanged();recomputeStatus();}
    public boolean mayView(Player p){return p instanceof ServerPlayer sp&&FurnaceAccess.canView(sp,this);}
    public boolean mayControl(Player p){return p instanceof ServerPlayer sp&&FurnaceAccess.canControl(sp,this);}
    public boolean mayConfigure(Player p){return p instanceof ServerPlayer sp&&FurnaceAccess.canConfigure(sp,this);}
    public boolean canAccess(ServerPlayer p,Permission permission){return FurnaceAccess.allowed(p,this,permission);}
    public long energyStored(){return energy.stored();}public long energyCapacity(){return energy.capacity();}public long currentEnergyDraw(){return draw;}
    public long processedTotal(){return processed;} public double storedXp(){return xp.doubleValue();}
    public int temperaturePercent(){return level!=null&&level.isClientSide?clientTemperature:(int)heat.temperature(FurnaceServerConfig.settings(tier()));}
    public FurnaceHeat.State visualHeat(){return level!=null&&level.isClientSide?clientHeat:heat.state();}
    public int activeJobs(){return (int)jobs.stream().filter(j->j!=null&&j.state!=FurnaceJobState.FINISHED_WAITING_OUTPUT).count();}
    public int maxJobs(){return FurnaceServerConfig.settings(tier()).parallelJobs();}
    public int pendingOutputs(){return (int)jobs.stream().filter(j->j!=null&&!j.pending.isEmpty()).count();}
    public int queuedItems(){return count(input)+recovery.stream().mapToInt(ItemStack::getCount).sum();}
    private static int count(ItemStackHandler h){int n=0;for(int i=0;i<h.getSlots();i++)n+=h.getStackInSlot(i).getCount();return n;}
    private static int usage(ItemStackHandler h){double n=0;for(int i=0;i<h.getSlots();i++){var s=h.getStackInSlot(i);if(!s.isEmpty())n+=(double)s.getCount()/Math.min(h.getSlotLimit(i),s.getMaxStackSize());}return (int)Math.round(100*n/h.getSlots());}
    public int inputUsage(){return usage(input);}public int outputUsage(){return usage(output);}
    private boolean matches(FurnaceLayout.Port p,BlockPos pos,Direction face){return face==p.face()&&FurnaceLayout.position(worldPosition,facing(),p.cell()).equals(pos);}
    public boolean isEnergyPort(BlockPos pos,Direction face){return matches(FurnaceLayout.energy(tier(),facing()),pos,face);}
    public ItemPort itemPort(BlockPos pos,Direction face){if(matches(FurnaceLayout.input(tier(),facing()),pos,face))return inputPort;if(matches(FurnaceLayout.output(tier(),facing()),pos,face))return outputPort;return null;}
    public Optional<RecipeHolder<SmeltingRecipe>> resolve(ItemStack stack){
        if(level==null||level.isClientSide||stack.isEmpty())return Optional.empty();
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING,new SingleRecipeInput(stack),level).filter(h->safe(h.value(),stack));
    }
    private boolean safe(SmeltingRecipe r,ItemStack stack){try{return !r.isSpecial()&&r.getCookingTime()>0&&r.getCookingTime()<=FurnaceEnergyCost.MAX_COOKING_TICKS&&Float.isFinite(r.getExperience())&&r.getExperience()>=0&&r.getExperience()<=1_000_000&&!r.assemble(new SingleRecipeInput(stack),level.registryAccess()).isEmpty();}catch(RuntimeException bad){return false;}}
    private boolean valid(FurnaceJob job){
        var holder=level.getRecipeManager().byKey(job.recipeId);
        if(holder.isEmpty()||!(holder.get().value() instanceof SmeltingRecipe recipe))return false;
        try{return safe(recipe,job.reservedInput)&&recipe.matches(new SingleRecipeInput(job.reservedInput),level)&&recipe.getCookingTime()==job.cookingTime&&Float.compare(recipe.getExperience(),job.xp)==0&&ItemStack.matches(recipe.assemble(new SingleRecipeInput(job.reservedInput),level.registryAccess()),job.expectedResult);}catch(RuntimeException bad){return false;}
    }
    private void revalidate(){
        refreshInvalidatedRecipes();
        for(int i=0;i<jobs.size();i++){var j=jobs.get(i);if(j!=null&&j.pending.isEmpty()&&!valid(j)){recordInvalidRecipe(j);recovery.add(j.reservedInput.copy());jobs.set(i,null);}}
        invalidRecipe=!invalidatedRecipes.isEmpty();inventoryDirty=true;setChanged();
    }
    private void recordInvalidRecipe(FurnaceJob job){
        if(!invalidatedRecipes.contains(job.recipeId)&&invalidatedRecipes.size()>=MAX_INVALIDATED_RECIPES){var oldest=invalidatedRecipes.iterator().next();invalidatedRecipes.remove(oldest);invalidatedInputs.remove(oldest);}
        invalidatedRecipes.add(job.recipeId);invalidatedInputs.put(job.recipeId,job.reservedInput.copyWithCount(1));invalidRecipe=true;
    }
    private void refreshInvalidatedRecipes(){
        boolean changed=false;
        for(var iterator=invalidatedRecipes.iterator();iterator.hasNext();){
            var id=iterator.next();var holder=level.getRecipeManager().byKey(id);var sample=invalidatedInputs.get(id);
            if(holder.isEmpty()||!(holder.get().value() instanceof SmeltingRecipe recipe)||sample==null)continue;
            try{if(safe(recipe,sample)&&recipe.matches(new SingleRecipeInput(sample),level)){iterator.remove();invalidatedInputs.remove(id);changed=true;}}catch(RuntimeException malformed){/* Keep warning until a safe recipe is restored. */}
        }
        invalidRecipe=!invalidatedRecipes.isEmpty();if(changed)setChanged();
    }
    private void recover(){for(var it=recovery.listIterator();it.hasNext();){var left=it.next().copy();for(int i=0;i<input.getSlots()&&!left.isEmpty();i++){var existing=input.getStackInSlot(i);if(!existing.isEmpty()&&!ItemStack.isSameItemSameComponents(existing,left))continue;int space=Math.min(input.getSlotLimit(i),left.getMaxStackSize())-existing.getCount();int n=Math.min(space,left.getCount());if(n>0){var merged=existing.isEmpty()?left.copyWithCount(n):existing.copyWithCount(existing.getCount()+n);input.setStackInSlot(i,merged);left.shrink(n);}}if(left.isEmpty())it.remove();else it.set(left);} }
    private void startJobs(){
        var cfg=FurnaceServerConfig.settings(tier());
        for(int lane:scheduler.laneOrder(maxJobs())){
            if(jobs.get(lane)!=null)continue;
            final var found=new java.util.concurrent.atomic.AtomicReference<RecipeHolder<SmeltingRecipe>>();
            int slot=scheduler.findInput(input.getSlots(),i->{var r=resolve(input.getStackInSlot(i));r.ifPresent(found::set);return r.isPresent();});
            if(slot<0)break;var holder=found.get();var recipe=holder.value();var one=input.getStackInSlot(slot).copyWithCount(1);
            var result=recipe.assemble(new SingleRecipeInput(one),level.registryAccess());
            var job=new FurnaceJob(UUID.randomUUID(),holder.id(),one,result,owner,DashboardAPI.production(level.getServer()).startStamp(),level.getGameTime(),recipe.getCookingTime(),FurnaceEnergyCost.duration(recipe.getCookingTime(),cfg.speedMultiplier()),FurnaceEnergyCost.operationEnergy(cfg.baseEnergyPer200Ticks(),recipe.getCookingTime()),recipe.getExperience());
            input.extractItem(slot,1,false);jobs.set(lane,job);setChanged();
        }
    }
    private void publish(FurnaceJob j){
        if(j.receiptPublished)return;
        j.receiptPublished=true;setChanged();
        if(j.producer==null)return;
        var log=DashboardAPI.production(level.getServer());
        log.publish(new ProductionReceipt(j.transactionId,j.producer,HomeLinkFurnace.id("industrial_furnace"),Optional.of(j.recipeId),j.expectedResult,j.startedTick,Math.max(j.startedTick,j.completedTick),log.nextSequence(),homeNetworkId(),Optional.ofNullable(j.start)));
    }
    private void flush(){for(int i=0;i<jobs.size();i++){var j=jobs.get(i);if(j==null||j.pending.isEmpty())continue;publish(j);var left=ItemHandlerHelper.insertItemStacked(output,j.pending,false);if(left.getCount()!=j.pending.getCount()){j.pending=left;setChanged();}if(j.pending.isEmpty()){jobs.set(i,null);inventoryDirty=true;setChanged();}}}
    public void collectXp(ServerPlayer player){if(!mayControl(player))return;int whole=xp.intValue();if(whole>0){xp=xp.subtract(java.math.BigDecimal.valueOf(whole));setChanged();player.giveExperiencePoints(whole);}}
    private boolean redstoneAllows(){
        if(redstone==RedstoneMode.IGNORE)return true;
        boolean signal=false;
        if(level!=null)for(var direction:Direction.values()){
            var neighbor=worldPosition.relative(direction);if(!level.hasChunkAt(neighbor))continue;
            var state=level.getBlockState(neighbor);
            int power=state.getSignal(level,neighbor,direction);
            // Equivalent to vanilla conductor power, with unloaded positions excluded.
            // Level.hasNeighborSignal/getSignal would otherwise fetch adjacent chunks.
            if(state.isRedstoneConductor(level,neighbor))for(var direct:Direction.values()){
                var source=neighbor.relative(direct);if(!level.hasChunkAt(source))continue;
                power=Math.max(power,level.getBlockState(source).getDirectSignal(level,source,direct));
            }
            if(power>0){signal=true;break;}
        }
        return redstone.allows(signal);
    }
    public boolean structureComplete(){return structureComplete;}public boolean structureLoaded(){return structureLoaded;}
    public void checkStructure(){
        structureLoaded=true;structureComplete=true;
        for(var cell:FurnaceLayout.cells(tier())){var p=FurnaceLayout.position(worldPosition,facing(),cell);if(!level.hasChunkAt(p)){structureLoaded=false;continue;}var s=level.getBlockState(p);if(!s.is(getBlockState().getBlock())||s.getValue(IndustrialFurnaceBlock.FACING)!=facing()||s.getValue(IndustrialFurnaceBlock.COLUMN)!=cell.column()||s.getValue(IndustrialFurnaceBlock.LAYER)!=cell.layer()||s.getValue(IndustrialFurnaceBlock.ROW)!=cell.row())structureComplete=false;}
    }
    private boolean working(){return enabled&&FurnaceServerConfig.settings(tier()).enabled()&&redstoneAllows()&&structureComplete&&structureLoaded;}
    private void recomputeStatus(){
        if(!enabled||!FurnaceServerConfig.settings(tier()).enabled())status=FurnaceStatus.SWITCHED_OFF;
        else if(!redstoneAllows())status=FurnaceStatus.REDSTONE_PAUSED;
        else if(!structureComplete||!structureLoaded)status=FurnaceStatus.INCOMPLETE_STRUCTURE;
        else if(invalidRecipe)status=FurnaceStatus.RECIPE_INVALID;
        else if(pendingOutputs()>0)status=FurnaceStatus.OUTPUT_FULL;
        else if(activeJobs()>0&&!heat.canProcess()&&!hasProcessingPower())status=FurnaceStatus.NO_POWER;
        else if(activeJobs()>0&&jobs.stream().anyMatch(j->j!=null&&j.state==FurnaceJobState.PAUSED_NO_POWER))status=FurnaceStatus.NO_POWER;
        else if(activeJobs()>0&&energy.stored()==0)status=FurnaceStatus.NO_POWER;
        else if(heat.state()==FurnaceHeat.State.WARMING)status=FurnaceStatus.WARMING;
        else if(heat.canProcess()&&activeJobs()>0)status=FurnaceStatus.PROCESSING;
        else if(heat.canProcess())status=FurnaceStatus.READY;
        else status=queuedItems()==0?FurnaceStatus.INPUT_EMPTY:FurnaceStatus.IDLE;
    }
    public static void serverTick(Level level,BlockPos pos,BlockState state,IndustrialFurnaceBlockEntity e){e.serverTick();}
    public void serverTick(){
        if(!(level instanceof ServerLevel server)||dismantled)return;ticks++;
        var dismantleRequest=FurnaceDismantleRequests.get(server).take(worldPosition,getBlockState());
        if(dismantleRequest.isPresent()){
            if(!dismantleRequest.get().dropMachine())markPlayerBreak();
            dismantle();return;
        }
        if(ticks==1||ticks%20==0)checkStructure();
        var recipes=level.getRecipeManager();long generation=FurnaceRecipeReloads.generation();
        if(lastRecipes!=recipes||recipeGeneration!=generation){lastRecipes=recipes;recipeGeneration=generation;revalidate();}
        if(device==null&&(ticks==1||ticks%100==0)){recomputeStatus();device=FurnaceHomeCore.register(server,this).orElse(null);}
        // Bounded slow revalidation also handles RecipeManager.apply replacing tables in-place during /reload.
        if(ticks%20==0)revalidateActiveOnly();
        if(inventoryDirty||ticks%20==0){recover();flush();if(working())startJobs();inventoryDirty=false;}
        draw=0;var cfg=FurnaceServerConfig.settings(tier());
        int previousHeatProgress=heat.progress;
        long heatCost=heat.tick(cfg,working(),activeJobs()>0,energy.stored());energy.consume(heatCost);draw+=heatCost;
        if(working()&&heat.canProcess())for(int lane:scheduler.laneOrder(jobs.size())){var j=jobs.get(lane);if(j==null||!j.pending.isEmpty())continue;long paid=j.advance(energy.stored());energy.consume(paid);draw+=paid;if(j.finish()){j.completedTick=level.getGameTime();xp=xp.add(new java.math.BigDecimal(Float.toString(j.xp))).min(java.math.BigDecimal.valueOf(cfg.maxStoredXp()));processed=processed==Long.MAX_VALUE?processed:processed+1;publish(j);inventoryDirty=true;}setChanged();}
        else for(var j:jobs)if(j!=null&&j.pending.isEmpty())j.state=!working()?FurnaceJobState.PAUSED_DISABLED:energy.stored()==0?FurnaceJobState.PAUSED_NO_POWER:FurnaceJobState.PROCESSING;
        // A produced result that fits is transferred in the same tick, so normal production
        // never creates a transient OUTPUT_FULL alarm. Refill freed lanes without an idle dip.
        if(inventoryDirty){flush();if(working()&&count(input)>0)startJobs();inventoryDirty=false;}
        recomputeStatus();
        // Heat is persistent whenever warming, holding or cooling, including ticks with fractional zero cost.
        if(previousHeatProgress>0||heat.progress>0)setChanged();
        if(device!=null&&(ticks%20==0||status!=lastVisualStatus))device.refresh();
        if(status!=lastVisualStatus){
            if(status==FurnaceStatus.PROCESSING)level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE,net.minecraft.sounds.SoundSource.BLOCKS,.12f,1.4f);
            else if(lastVisualStatus==FurnaceStatus.PROCESSING)level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,net.minecraft.sounds.SoundSource.BLOCKS,.10f,1.4f);
        }
        if(ticks%80==0&&heat.canProcess())level.playSound(null,worldPosition,net.minecraft.sounds.SoundEvents.BEACON_AMBIENT,net.minecraft.sounds.SoundSource.BLOCKS,.03f,1.5f);
        if(status!=lastVisualStatus||ticks%20==0&&(temperaturePercent()!=lastVisualTemperature||heat.state()!=lastVisualHeat)){lastVisualStatus=status;lastVisualTemperature=temperaturePercent();lastVisualHeat=heat.state();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    }
    private FurnaceStatus lastVisualStatus;
    private int lastVisualTemperature=-1;
    private FurnaceHeat.State lastVisualHeat;
    private void revalidateActiveOnly(){refreshInvalidatedRecipes();boolean changed=false;for(int i=0;i<jobs.size();i++){var j=jobs.get(i);if(j!=null&&j.pending.isEmpty()&&!valid(j)){recordInvalidRecipe(j);recovery.add(j.reservedInput.copy());jobs.set(i,null);changed=true;}}if(changed){setChanged();inventoryDirty=true;}}
    public void markPlayerBreak(){playerBreak=true;}
    public void dismantle(){
        if(dismantled||!(level instanceof ServerLevel server))return;dismantled=true;
        if(!playerBreak)Block.popResource(level,worldPosition,new ItemStack(getBlockState().getBlock()));
        for(var h:List.of(input,output))for(int i=0;i<h.getSlots();i++){var s=h.getStackInSlot(i);if(!s.isEmpty())Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,s.copy());h.setStackInSlot(i,ItemStack.EMPTY);}
        for(var j:jobs)if(j!=null){var s=j.pending.isEmpty()?j.reservedInput:j.pending;Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,s.copy());}Collections.fill(jobs,null);
        for(var s:recovery)Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,s.copy());recovery.clear();
        int experience=xp.intValue();xp=java.math.BigDecimal.ZERO;if(experience>0)ExperienceOrb.award(server,Vec3.atCenterOf(worldPosition),experience);
        if(device!=null)FurnaceHomeCore.unregister(server,device);FurnaceHomeCore.forgetOnRemoval(server,this);
        for(var cell:FurnaceLayout.cells(tier())){var p=FurnaceLayout.position(worldPosition,facing(),cell);if(level.hasChunkAt(p)&&level.getBlockState(p).is(getBlockState().getBlock()))level.removeBlock(p,false);}
        setChanged();
    }
    @Override public void setRemoved(){if(device!=null&&level instanceof ServerLevel server)FurnaceHomeCore.unregister(server,device);device=null;super.setRemoved();}
    @Override public void onLoad(){super.onLoad();ticks=0;lastRecipes=null;inventoryDirty=true;invalidatePartCapabilities();}
    private void invalidatePartCapabilities(){
        if(level==null||level.isClientSide)return;
        for(var cell:FurnaceLayout.cells(tier())){
            var p=FurnaceLayout.position(worldPosition,facing(),cell);if(!level.hasChunkAt(p))continue;
            level.invalidateCapabilities(p);
            // Notify loaded logistics graphs without dispatching block updates into unloaded chunks.
            var neighbors=EnumSet.noneOf(Direction.class);
            for(var direction:Direction.values())if(level.hasChunkAt(p.relative(direction)))neighbors.add(direction);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent(level,p,getBlockState(),neighbors,false));
        }
    }
    @Override public void onChunkUnloaded(){invalidatePartCapabilities();if(device!=null&&level instanceof ServerLevel server)FurnaceHomeCore.unregister(server,device);device=null;super.onChunkUnloaded();}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.saveAdditional(tag,registries);tag.putInt("version",1);tag.putUUID("device",deviceId);if(owner!=null)tag.putUUID("owner",owner);if(network!=null)tag.putUUID("network",network);
        tag.putString("ownerName",ownerName);tag.putString("networkName",networkName);tag.putString("name",customName);tag.putBoolean("enabled",enabled);tag.putString("redstone",redstone.name());energy.save(tag,"energy");tag.put("heat",heat.save());tag.putDouble("xp",storedXp());tag.putString("xpExact",xp.toPlainString());tag.putLong("processed",processed);
        tag.put("input",input.serializeNBT(registries));tag.put("output",output.serializeNBT(registries));
        var list=new ListTag();for(int i=0;i<jobs.size();i++)if(jobs.get(i)!=null){var j=jobs.get(i).save(registries);j.putInt("lane",i);list.add(j);}tag.put("jobs",list);
        var refunds=new ListTag();for(var s:recovery)if(!s.isEmpty())refunds.add(s.save(registries));tag.put("recovery",refunds);tag.putInt("nextInput",scheduler.inputCursor());tag.putInt("nextLane",scheduler.laneCursor());
        var invalid=new ListTag();for(var id:invalidatedRecipes){var t=new CompoundTag();t.putString("recipe",id.toString());var sample=invalidatedInputs.get(id);if(sample!=null&&!sample.isEmpty())t.put("input",sample.save(registries));invalid.add(t);}tag.put("invalidRecipes",invalid);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){
        super.loadAdditional(tag,registries);if(tag.hasUUID("device"))deviceId=tag.getUUID("device");owner=tag.hasUUID("owner")?tag.getUUID("owner"):null;network=tag.hasUUID("network")?tag.getUUID("network"):null;
        ownerName=limited(tag.getString("ownerName"),64);networkName=limited(tag.getString("networkName"),128);customName=limited(tag.getString("name"),64);enabled=!tag.contains("enabled")||tag.getBoolean("enabled");
        try{redstone=RedstoneMode.valueOf(tag.getString("redstone"));}catch(IllegalArgumentException bad){redstone=RedstoneMode.IGNORE;}
        energy.setStored(tag.getLong("energy"));heat.load(tag.getCompound("heat"),FurnaceServerConfig.settings(tier()));
        xp=java.math.BigDecimal.ZERO;
        try{String exact=tag.getString("xpExact");if(exact.length()<=64&&exact.matches("[0-9]{1,10}(\\.[0-9]{1,48})?"))xp=new java.math.BigDecimal(exact);else if(Double.isFinite(tag.getDouble("xp")))xp=java.math.BigDecimal.valueOf(tag.getDouble("xp"));}catch(NumberFormatException ignored){}
        xp=xp.max(java.math.BigDecimal.ZERO).min(java.math.BigDecimal.valueOf(FurnaceServerConfig.settings(tier()).maxStoredXp())).stripTrailingZeros();processed=Math.max(0,tag.getLong("processed"));
        loadInventory(input,tag.getCompound("input"),registries);loadInventory(output,tag.getCompound("output"),registries);Collections.fill(jobs,null);recovery.clear();
        var list=tag.getList("jobs",Tag.TAG_COMPOUND);Set<UUID> transactions=new HashSet<>();for(int i=0;i<list.size();i++){var t=list.getCompound(i);var loaded=FurnaceJob.load(t,registries);if(loaded.isPresent()){var j=loaded.get();if(!transactions.add(j.transactionId))continue;int lane=t.getInt("lane");if(lane>=0&&lane<jobs.size()&&jobs.get(lane)==null)jobs.set(lane,j);else recovery.add((j.pending.isEmpty()?j.reservedInput:j.pending).copy());}else{var key=t.contains("pending")?"pending":"reserved";var s=safeStack(registries,t.getCompound(key));if(!s.isEmpty())recovery.add(s);}}
        var refunds=tag.getList("recovery",Tag.TAG_COMPOUND);for(int i=0;i<refunds.size();i++){var s=safeStack(registries,refunds.getCompound(i));if(!s.isEmpty())recovery.add(s);}
        invalidatedRecipes.clear();invalidatedInputs.clear();var invalid=tag.getList("invalidRecipes",Tag.TAG_COMPOUND);
        for(int i=0;i<invalid.size()&&invalidatedRecipes.size()<MAX_INVALIDATED_RECIPES;i++){var t=invalid.getCompound(i);var id=ResourceLocation.tryParse(t.getString("recipe"));var sample=safeStack(registries,t.getCompound("input"));if(id!=null&&!sample.isEmpty()){invalidatedRecipes.add(id);invalidatedInputs.put(id,sample.copyWithCount(1));}}
        invalidRecipe=!invalidatedRecipes.isEmpty();scheduler.restore(tag.getInt("nextInput"),tag.getInt("nextLane"));lastRecipes=null;inventoryDirty=true;
    }
    private static ItemStack safeStack(HolderLookup.Provider registries,CompoundTag tag){try{return ItemStack.parseOptional(registries,tag);}catch(RuntimeException malformed){return ItemStack.EMPTY;}}
    private static String limited(String s,int n){return s.length()>n?s.substring(0,n):s;}
    private static void loadInventory(ItemStackHandler inventory,CompoundTag tag,HolderLookup.Provider registries){for(int i=0;i<inventory.getSlots();i++)inventory.setStackInSlot(i,ItemStack.EMPTY);var list=tag.getList("Items",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++){var t=list.getCompound(i);int slot=t.getInt("Slot");if(slot>=0&&slot<inventory.getSlots()){try{var s=ItemStack.parseOptional(registries,t);if(!s.isEmpty())inventory.setStackInSlot(slot,s.copyWithCount(Math.min(s.getCount(),s.getMaxStackSize())));}catch(RuntimeException ignored){}}}}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var t=new CompoundTag();t.putInt("temperature",temperaturePercent());t.putString("visualHeat",heat.state().name());return t;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public void onDataPacket(net.minecraft.network.Connection connection,ClientboundBlockEntityDataPacket packet,HolderLookup.Provider registries){if(packet.getTag()!=null)handleUpdateTag(packet.getTag(),registries);}
    @Override public void handleUpdateTag(CompoundTag tag,HolderLookup.Provider registries){clientTemperature=Math.clamp(tag.getInt("temperature"),0,100);try{clientHeat=FurnaceHeat.State.valueOf(tag.getString("visualHeat"));}catch(IllegalArgumentException ignored){clientHeat=FurnaceHeat.State.COLD;}}
    public int menuValue(int index){
        if(index>=19&&index<=26){int lane=index-19;var j=lane<jobs.size()?jobs.get(lane):null;return j==null?0:(int)(j.progressRatio()*10000);}
        return switch(index){case 0->maxJobs();case 1->status.ordinal();case 2->heat.state().ordinal();case 3->temperaturePercent()*100;case 4->enabled?1:0;case 5->redstone.ordinal();case 6->level instanceof ServerLevel server&&homeNetworkId().flatMap(id->DashboardAPI.networks(server.getServer()).getNetwork(id)).filter(n->n.devices().contains(deviceId)).isPresent()?1:0;case 7->activeJobs();case 8->pendingOutputs();case 9->Math.min(65535,queuedItems());case 10->(int)energy.stored();case 11->(int)(energy.stored()>>>32);case 12->(int)energy.capacity();case 13->(int)(energy.capacity()>>>32);case 14->(int)draw;case 15->(int)((long)(storedXp()*1000));case 16->(int)(((long)(storedXp()*1000))>>>32);case 17->(int)processed;case 18->(int)(processed>>>32);case 27->(int)FurnaceServerConfig.settings(tier()).idleHeatEnergyPerMinute();case 28->(int)FurnaceServerConfig.settings(tier()).baseEnergyPer200Ticks();default->0;};
    }
}
