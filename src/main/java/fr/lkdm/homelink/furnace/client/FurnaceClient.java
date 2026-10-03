package fr.lkdm.homelink.furnace.client;

import fr.lkdm.homelink.furnace.HomeLinkFurnace;
import fr.lkdm.homelink.furnace.registry.FurnaceRegistries;
import fr.lkdm.homelink.furnace.network.FurnacePayloads;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

@EventBusSubscriber(modid = HomeLinkFurnace.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FurnaceClient {
    private FurnaceClient() { }
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event) { event.register(FurnaceRegistries.MENU.get(), FurnaceScreen::new); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) { event.registerBlockEntityRenderer(FurnaceRegistries.MASTER.get(), FurnaceRenderer::new); }
    @SubscribeEvent public static void models(ModelEvent.RegisterAdditional event) { FurnaceRenderer.registerModels(event); }
    @EventBusSubscriber(modid = HomeLinkFurnace.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
    public static final class Session {
        @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { FurnacePayloads.ClientChoices.clear(); }
    }
}
