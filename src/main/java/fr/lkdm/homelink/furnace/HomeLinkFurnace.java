package fr.lkdm.homelink.furnace;

import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import fr.lkdm.homelink.furnace.config.FurnaceServerConfig;
import fr.lkdm.homelink.furnace.homelink.FurnaceHomeCore;
import fr.lkdm.homelink.furnace.network.FurnacePayloads;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(HomeLinkFurnace.MOD_ID)
public final class HomeLinkFurnace {
    public static final String MOD_ID = "homelink_furnace";
    public static net.minecraft.resources.ResourceLocation id(String path) {return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID,path);}
    public HomeLinkFurnace(IEventBus bus, ModContainer container) {
        FurnaceRegistries.register(bus);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(fr.lkdm.homelink.furnace.furnace.FurnaceRecipeReloads::onSync);
        container.registerConfig(ModConfig.Type.SERVER, FurnaceServerConfig.SPEC);
        bus.addListener(FurnaceRegistries::capabilities);
        bus.addListener(FurnacePayloads::register);
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(FurnaceHomeCore::registerProviders));
    }
}
