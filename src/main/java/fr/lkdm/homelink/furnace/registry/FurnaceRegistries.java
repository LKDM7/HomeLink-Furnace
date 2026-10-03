package fr.lkdm.homelink.furnace.registry;

import fr.lkdm.homelink.furnace.HomeLinkFurnace;
import fr.lkdm.homelink.furnace.block.*;
import fr.lkdm.homelink.furnace.blockentity.IndustrialFurnaceBlockEntity;
import fr.lkdm.homelink.furnace.menu.FurnaceMenu;
import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.item.ItemApi;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

public final class FurnaceRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomeLinkFurnace.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomeLinkFurnace.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomeLinkFurnace.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HomeLinkFurnace.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeLinkFurnace.MOD_ID);
    public static final DeferredBlock<IndustrialFurnaceBlock> FURNACE_I = block(FurnaceTier.I);
    public static final DeferredBlock<IndustrialFurnaceBlock> FURNACE_II = block(FurnaceTier.II);
    public static final DeferredBlock<IndustrialFurnaceBlock> FURNACE_III = block(FurnaceTier.III);
    public static final List<DeferredBlock<IndustrialFurnaceBlock>> FURNACES = List.of(FURNACE_I,FURNACE_II,FURNACE_III);
    public static final DeferredItem<BlockItem> FURNACE_I_ITEM = item(FURNACE_I);
    public static final DeferredItem<BlockItem> FURNACE_II_ITEM = item(FURNACE_II);
    public static final DeferredItem<BlockItem> FURNACE_III_ITEM = item(FURNACE_III);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IndustrialFurnaceBlockEntity>> MASTER = TYPES.register("industrial_furnace", () -> BlockEntityType.Builder.of(IndustrialFurnaceBlockEntity::new,FURNACE_I.get(),FURNACE_II.get(),FURNACE_III.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<FurnaceMenu>> MENU = MENUS.register("furnace", () -> IMenuTypeExtension.create(FurnaceMenu::new));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB = TABS.register("furnace", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.homelink_furnace")).icon(() -> FURNACE_I_ITEM.get().getDefaultInstance()).displayItems((p,o) -> { o.accept(FURNACE_I_ITEM.get());o.accept(FURNACE_II_ITEM.get());o.accept(FURNACE_III_ITEM.get()); }).build());
    private static DeferredBlock<IndustrialFurnaceBlock> block(FurnaceTier tier) { return BLOCKS.register("industrial_furnace_"+(tier.ordinal()+1), () -> new IndustrialFurnaceBlock(tier, BlockBehaviour.Properties.of().strength(4,8).requiresCorrectToolForDrops().sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK))); }
    private static DeferredItem<BlockItem> item(DeferredBlock<IndustrialFurnaceBlock> block) { return ITEMS.register(block.getId().getPath(), () -> new FurnaceBlockItem(block.get(),new Item.Properties())); }
    public static void register(IEventBus bus) { BLOCKS.register(bus);ITEMS.register(bus);TYPES.register(bus);MENUS.register(bus);TABS.register(bus); }
    public static void capabilities(RegisterCapabilitiesEvent event) {
        var blocks = FURNACES.stream().map(DeferredBlock::get).toArray(net.minecraft.world.level.block.Block[]::new);
        event.registerBlock(EnergyApi.BLOCK,(level,pos,state,be,face) -> { var master=IndustrialFurnaceBlock.master(level,pos,state); return master!=null && master.isEnergyPort(pos,face) ? master.capabilityEnergyPort() : null; },blocks);
        event.registerBlock(ItemApi.BLOCK,(level,pos,state,be,face) -> { var master=IndustrialFurnaceBlock.master(level,pos,state); return master==null ? null : master.itemPort(pos,face); },blocks);
        event.registerBlock(Capabilities.ItemHandler.BLOCK,(level,pos,state,be,face) -> { var master=IndustrialFurnaceBlock.master(level,pos,state); return master==null ? null : master.itemPort(pos,face); },blocks);
    }
}
